package org.projectcontinuum.core.commons.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.validation.loop.LoopScopes

/** The units the workflow validator delegates to, each checked on its own. */
class WorkflowValidatorUnitsTest {

  private val table = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  private val step = ContinuumWorkflowModel.NodeData(
    description = "",
    title = "Step",
    nodeModel = "org.test.Step",
    inputs = mapOf("in" to table),
    outputs = mapOf("out" to table),
    properties = mapOf("width" to 10),
    propertiesSchema = mapOf(
      "type" to "object",
      "additionalProperties" to false,
      "properties" to mapOf("width" to mapOf("type" to "integer"), "name" to mapOf("type" to "string", "minLength" to 1)),
      "required" to listOf("name")
    )
  )

  private fun resolved(vararg keys: String): Map<String, ResolvedNode> =
    keys.associateWith { key -> ResolvedNode(key = key, nodeId = "org.test.Step", descriptor = step) }

  private fun edge(from: String, to: String, fromPort: String = "out", toPort: String = "in") =
    GraphEdge(from = from, fromPort = fromPort, to = to, toPort = toPort)

  private fun checkedGraph(resolvedNodes: Map<String, ResolvedNode>, validEdges: List<GraphEdge>, mode: ValidationMode = ValidationMode.RUN) =
    CheckedGraph(
      mode = mode,
      nodes = CheckedNodes(resolvedNodes = resolvedNodes, issues = RuleIssues()),
      loopScopes = LoopScopes.of(
        graphNodes = resolvedNodes.values.map { node -> GraphNode(key = node.key, nodeId = node.nodeId) },
        acceptedKeys = resolvedNodes.keys
      ),
      edges = CheckedEdges(validEdges = validEdges, issues = RuleIssues())
    )

  @Test
  fun `authoring property checks report an empty required value once and leave additionalProperties to the unknown-key message`() {
    val issues = PropertyRules.issues(
      key = "a",
      given = mapOf("name" to "", "extra" to 1),
      descriptor = step,
      mode = ValidationMode.AUTHORING
    )

    assertEquals(
      listOf(
        "Node 'a': unknown property 'extra'. Known properties: width, name.",
        "Node 'a': required property 'name' has no value. Set it in properties."
      ),
      issues.map { issue -> issue.message }
    )
    assertEquals(listOf("extra", "name"), issues.map { issue -> issue.property })
  }

  @Test
  fun `run property checks report every schema violation of the properties as sent`() {
    val issues = PropertyRules.issues(key = "a", given = mapOf("name" to "", "extra" to 1), descriptor = step, mode = ValidationMode.RUN)

    assertEquals(setOf("name", "extra"), issues.map { issue -> issue.property }.toSet(), issues.toString())
    assertTrue(issues.all { issue -> issue.message.startsWith("Node 'a' properties: ") }, issues.toString())
  }

  @Test
  fun `an empty schema is not checked in either mode`() {
    val schemaless = step.copy(propertiesSchema = emptyMap())

    assertTrue(PropertyRules.issues(key = "a", given = mapOf("anything" to 1), descriptor = schemaless, mode = ValidationMode.RUN).isEmpty())
    assertEquals(
      listOf("Node 'a': unknown property 'anything'. Known properties: (none)."),
      PropertyRules.issues(key = "a", given = mapOf("anything" to 1), descriptor = schemaless, mode = ValidationMode.AUTHORING).map { issue -> issue.message }
    )
  }

  @Test
  fun `reserved declared ports are reported inputs first, then outputs`() {
    val definition = step.copy(inputs = mapOf("\$in" to table, "in" to table), outputs = mapOf("\$out" to table))

    val issues = ReservedIdRules.declaredPortIssues(key = "a", definition = definition, source = "submitted node")

    assertEquals(
      listOf("declares input port '\$in'", "declares output port '\$out'"),
      issues.map { issue -> issue.message.substringAfter("submitted node ").substringBefore(".") }
    )
    assertTrue(ReservedIdRules.nodeKeyIssue(key = "plain") == null)
  }

  @Test
  fun `edge checks return only the edges that pass every check, in the given order`() {
    val loopScopes = LoopScopes.of(graphNodes = listOf(GraphNode(key = "a", nodeId = "x")), acceptedKeys = setOf("a"))
    val edges = listOf(
      edge(from = "a", to = "b"),
      edge(from = "b", to = "a", fromPort = "nope"),
      edge(from = "a", to = "b"),
      edge(from = "c", to = "b"),
      edge(from = "b", to = "c")
    )

    val checked = EdgeChecks(resolvedNodes = resolved("a", "b", "c"), loopScopes = loopScopes).check(edges = edges)

    assertEquals(listOf(edge(from = "a", to = "b"), edge(from = "b", to = "c")), checked.validEdges)
    assertEquals(3, checked.issues.errors.size, checked.issues.errors.toString())
    assertTrue(checked.issues.warnings.isEmpty())
  }

  @Test
  fun `an edge rejected for its source port still occupies its input port`() {
    val loopScopes = LoopScopes.of(graphNodes = emptyList(), acceptedKeys = emptySet())
    val edges = listOf(edge(from = "a", to = "b", fromPort = "nope"), edge(from = "c", to = "b"))

    val checked = EdgeChecks(resolvedNodes = resolved("a", "b", "c"), loopScopes = loopScopes).check(edges = edges)

    assertTrue(checked.validEdges.isEmpty())
    assertEquals(
      "Edge c.out -> b.in: input port 'in' of node 'b' is already fed by a.nope. Each input port takes exactly one edge.",
      checked.issues.errors.last().message
    )
  }

  @Test
  fun `an edge to a loop is one error and its ports are not checked`() {
    val nodes = listOf(GraphNode(key = "a", nodeId = "org.test.Step"), GraphNode(key = "L", nodeId = LoopNode.NODE_MODEL))
    val loopScopes = LoopScopes.of(graphNodes = nodes, acceptedKeys = setOf("a", "L"))
    val resolvedNodes = resolved("a") + ("L" to ResolvedNode(key = "L", nodeId = LoopNode.NODE_MODEL, descriptor = null))

    val checked = EdgeChecks(resolvedNodes = resolvedNodes, loopScopes = loopScopes)
      .check(edges = listOf(edge(from = "a", to = "L", toPort = "\$error")))

    assertEquals(listOf("Edge a.out -> L.\$error: 'L' is a loop. A loop has no ports; connect the nodes inside it instead."), checked.issues.errors.map { issue -> issue.message })
    assertTrue(checked.validEdges.isEmpty())
  }

  @Test
  fun `the cycle rule names every node left over by Kahn's algorithm`() {
    val graph = checkedGraph(
      resolvedNodes = resolved("a", "b", "c", "d"),
      validEdges = listOf(edge(from = "a", to = "b"), edge(from = "b", to = "c"), edge(from = "c", to = "b"), edge(from = "c", to = "d"))
    )

    val issues = CycleRule.check(graph = graph)

    assertEquals(
      listOf("The edges contain a cycle through nodes 'b', 'c', 'd'. A workflow must be a directed acyclic graph."),
      issues.errors.map { issue -> issue.message }
    )
  }

  @Test
  fun `an acyclic graph without loops has no cycle issue`() {
    val graph = checkedGraph(resolvedNodes = resolved("a", "b"), validEdges = listOf(edge(from = "a", to = "b")))

    assertTrue(CycleRule.check(graph = graph).errors.isEmpty())
  }

  @Test
  fun `connection warnings word an unfed input by mode and skip a single-node workflow's not-connected warning`() {
    val single = checkedGraph(resolvedNodes = resolved("a"), validEdges = emptyList(), mode = ValidationMode.AUTHORING)
    val pair = checkedGraph(resolvedNodes = resolved("a", "b"), validEdges = emptyList(), mode = ValidationMode.RUN)

    assertEquals(
      listOf("Node 'a': input port 'in' has no incoming edge. The node may fail if it needs this input."),
      ConnectionWarningRule.check(graph = single).warnings.map { issue -> issue.message }
    )
    assertEquals(
      listOf(
        "Node 'a' is not connected to any other node.",
        "Node 'a': input port 'in' has no incoming edge. The node is skipped at run time, so nodes that depend on it do not run.",
        "Node 'b' is not connected to any other node.",
        "Node 'b': input port 'in' has no incoming edge. The node is skipped at run time, so nodes that depend on it do not run."
      ),
      ConnectionWarningRule.check(graph = pair).warnings.map { issue -> issue.message }
    )
  }

  @Test
  fun `loop properties are merged over the defaults in authoring mode and taken as sent in run mode`() {
    val loopDefinition = step.copy(properties = mapOf("condition" to "default", "maxIterations" to 50))
    val loop = ResolvedNode(
      key = "L",
      nodeId = LoopNode.NODE_MODEL,
      descriptor = loopDefinition,
      properties = mapOf("maxIterations" to 7),
      propertyIssues = listOf(WorkflowIssue(message = "bad", node = "L", property = "condition"))
    )

    val authoring = LoopWorkflowRules.loopProperties(graph = checkedGraph(resolvedNodes = mapOf("L" to loop), validEdges = emptyList(), mode = ValidationMode.AUTHORING)).single()
    val run = LoopWorkflowRules.loopProperties(graph = checkedGraph(resolvedNodes = mapOf("L" to loop), validEdges = emptyList(), mode = ValidationMode.RUN)).single()

    assertEquals("default", authoring.condition)
    assertEquals(7, authoring.maxIterations)
    assertTrue(authoring.schemaRejected)
    assertEquals(null, run.condition)
    assertEquals(7, run.maxIterations)
  }
}
