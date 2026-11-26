package org.projectcontinuum.core.commons.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionResultType
import org.projectcontinuum.core.commons.validation.loop.LoopConditionValidator

/**
 * Pins the complete output of [WorkflowValidator] for workflows that break nearly every rule at once: every message,
 * its location (node, edge, property) and the order of errors and warnings. Callers show the issues in this order and
 * the api-server tests assert the texts, so a change here is a client-visible change.
 */
class WorkflowValidatorIssueOrderTest {

  private val table = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  private val step = ContinuumWorkflowModel.NodeData(
    description = "",
    title = "Step",
    nodeModel = STEP,
    inputs = mapOf("in" to table),
    outputs = mapOf("out" to table),
    properties = mapOf("width" to 10),
    propertiesSchema = mapOf(
      "type" to "object",
      "properties" to mapOf("width" to mapOf("type" to "integer"), "name" to mapOf("type" to "string", "minLength" to 1)),
      "required" to listOf("name")
    )
  )

  private val source = ContinuumWorkflowModel.NodeData(
    description = "",
    title = "Source",
    nodeModel = SOURCE,
    outputs = mapOf("out" to table)
  )

  private val legacy = ContinuumWorkflowModel.NodeData(
    description = "",
    title = "Legacy",
    nodeModel = LEGACY,
    inputs = mapOf("\$in" to table),
    outputs = mapOf("out" to table, "\$legacy" to table)
  )

  private companion object {
    const val STEP = "org.test.Step"
    const val SOURCE = "org.test.Source"
    const val LEGACY = "org.test.Legacy"
    const val PINNED_ISSUES_RESOURCE = "validation/workflow-validator-issues.txt"
  }

  private val descriptors = NodeDescriptorSource { nodeModel ->
    when (nodeModel) {
      STEP -> step
      SOURCE -> source
      LEGACY -> legacy
      LoopNode.NODE_MODEL -> LoopNodeDescriptor.nodeData
      else -> null
    }
  }

  private val conditionValidator = LoopConditionValidator { text ->
    when (text) {
      "bad" -> LoopConditionCheckResult.Invalid(code = LoopConditionIssueCode.SYNTAX, message = "is not a valid CEL expression: boom", column = 2)
      "true" -> LoopConditionCheckResult.Valid(resultType = LoopConditionResultType.BOOL, readVariables = emptySet())
      else -> LoopConditionCheckResult.Valid(resultType = LoopConditionResultType.BOOL, readVariables = setOf("hasMore"))
    }
  }

  private val validator = WorkflowValidator(conditionValidator = conditionValidator, descriptors = descriptors)

  private fun loop(key: String, properties: Map<String, Any?>, parent: String? = null) =
    GraphNode(key = key, nodeId = LoopNode.NODE_MODEL, properties = properties, type = LoopNode.NODE_TYPE, parent = parent)

  private fun edge(from: String?, fromPort: String?, to: String?, toPort: String?) =
    GraphEdge(from = from, fromPort = fromPort, to = to, toPort = toPort)

  /** Every rule broken once, with a plain cycle (so the collapsed-loop cycle check does not run). */
  private val brokenNodes = listOf(
    GraphNode(key = null, nodeId = STEP),
    GraphNode(key = "a", nodeId = STEP, properties = mapOf("name" to "n")),
    GraphNode(key = "a", nodeId = SOURCE),
    GraphNode(key = "\$x", nodeId = STEP, properties = mapOf("name" to "n")),
    GraphNode(key = "noid", nodeId = null),
    GraphNode(key = "ghost", nodeId = "org.test.Missing"),
    GraphNode(key = "legacy", nodeId = LEGACY, properties = emptyMap()),
    GraphNode(
      key = "emb",
      nodeId = STEP,
      properties = mapOf("width" to "wide", "extra" to 1, "name" to ""),
      embedded = step.copy(propertiesSchema = emptyMap(), inputs = mapOf("\$in" to table))
    ),
    GraphNode(key = "b", nodeId = STEP, properties = emptyMap(), type = LoopNode.NODE_TYPE),
    loop(key = "L", properties = LoopNodeDescriptor.defaultProperties + ("condition" to "bad")),
    GraphNode(key = "m1", nodeId = STEP, properties = mapOf("name" to "n"), parent = "L"),
    GraphNode(key = "m2", nodeId = SOURCE, properties = emptyMap(), parent = "nowhere", type = "Group"),
    loop(key = "E", properties = LoopNodeDescriptor.defaultProperties + ("condition" to "true")),
    loop(key = "big", properties = LoopNodeDescriptor.defaultProperties + ("maxIterations" to 10_000)),
    GraphNode(key = "bm", nodeId = STEP, properties = mapOf("name" to "n"), parent = "big"),
    GraphNode(key = "lonely", nodeId = SOURCE, properties = emptyMap())
  )

  private val brokenEdges = listOf(
    edge(from = null, fromPort = "out", to = "a", toPort = "in"),
    edge(from = "a", fromPort = "out", to = "L", toPort = "in"),
    edge(from = "zz", fromPort = "out", to = "qq", toPort = "in"),
    edge(from = "a", fromPort = "out", to = "a", toPort = "in"),
    edge(from = "a", fromPort = "\$variable", to = "b", toPort = "\$error"),
    edge(from = "a", fromPort = "nope", to = "b", toPort = "in"),
    edge(from = "a", fromPort = "out", to = "b", toPort = "in"),
    edge(from = "a", fromPort = "out", to = "b", toPort = "in"),
    edge(from = "emb", fromPort = "out", to = "\$x", toPort = "in"),
    edge(from = "\$x", fromPort = "out", to = "emb", toPort = "in"),
    edge(from = "legacy", fromPort = "out", to = "m1", toPort = "in")
  )

  /** One line per issue: kind, node, edge, property, message. */
  private fun render(result: WorkflowValidationResult): List<String> {
    fun line(kind: String, issue: WorkflowIssue) = "$kind|${issue.node}|${issue.edge}|${issue.property}|${issue.message}"
    return result.errors.map { issue -> line(kind = "E", issue = issue) } + result.warnings.map { issue -> line(kind = "W", issue = issue) }
  }

  private fun renderedFor(nodes: List<GraphNode>, edges: List<GraphEdge>, mode: ValidationMode): List<String> =
    render(result = validator.validate(nodes = nodes, edges = edges, mode = mode))

  /** The expected lines of one section (`== <name> <mode>`) of the pinned issue file. */
  private fun expectedSection(name: String, mode: ValidationMode): List<String> {
    val resource = javaClass.classLoader.getResource(PINNED_ISSUES_RESOURCE) ?: error("$PINNED_ISSUES_RESOURCE is missing")
    val lines = resource.readText(charset = Charsets.UTF_8).lines()
    val start = lines.indexOf("== $name $mode") + 1
    check(start > 0) { "section '$name $mode' is missing in $PINNED_ISSUES_RESOURCE" }
    return lines.drop(start).takeWhile { line -> !line.startsWith("==") && line.isNotEmpty() }
  }

  @Test
  fun `a workflow that breaks every node, edge and loop rule gets the pinned issues in the pinned order`() {
    for (mode in ValidationMode.entries) {
      assertEquals(
        expectedSection(name = "broken", mode = mode).joinToString(separator = "\n"),
        renderedFor(nodes = brokenNodes, edges = brokenEdges, mode = mode).joinToString(separator = "\n"),
        "mode $mode"
      )
    }
  }

  @Test
  fun `a path that leaves a loop and comes back is the one collapsed-cycle error`() {
    for (mode in ValidationMode.entries) {
      assertEquals(
        expectedSection(name = "collapsed", mode = mode),
        renderedFor(nodes = collapsedNodes, edges = collapsedEdges, mode = mode),
        "mode $mode"
      )
    }
  }

  @Test
  fun `an unregistered node type is looked up once however many nodes use it`() {
    val lookups = mutableListOf<String>()
    val countingValidator = WorkflowValidator { nodeModel ->
      lookups += nodeModel
      descriptors.find(nodeModel)
    }

    countingValidator.validate(
      nodes = listOf(
        GraphNode(key = "a", nodeId = "org.test.Missing"),
        GraphNode(key = "b", nodeId = "org.test.Missing"),
        GraphNode(key = "c", nodeId = STEP, properties = mapOf("name" to "n")),
        GraphNode(key = "d", nodeId = STEP, properties = mapOf("name" to "n"))
      ),
      edges = emptyList(),
      mode = ValidationMode.RUN
    )

    assertEquals(listOf("org.test.Missing", STEP), lookups)
  }

  /** A path that leaves a loop and comes back into it: the plain graph is acyclic, the collapsed one is not. */
  private val collapsedNodes = listOf(
    GraphNode(key = "seed", nodeId = SOURCE, properties = emptyMap()),
    loop(key = "L", properties = LoopNodeDescriptor.defaultProperties),
    GraphNode(key = "fetch", nodeId = STEP, properties = mapOf("name" to "n"), parent = "L"),
    GraphNode(key = "parse", nodeId = STEP, properties = mapOf("name" to "n"), parent = "L"),
    GraphNode(key = "outside", nodeId = STEP, properties = mapOf("name" to "n"))
  )

  private val collapsedEdges = listOf(
    edge(from = "seed", fromPort = "out", to = "fetch", toPort = "in"),
    edge(from = "fetch", fromPort = "out", to = "outside", toPort = "in"),
    edge(from = "outside", fromPort = "out", to = "parse", toPort = "in")
  )
}
