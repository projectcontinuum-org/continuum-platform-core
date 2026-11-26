package org.projectcontinuum.core.api.server.mcp

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.springframework.ai.mcp.annotation.McpTool
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Loops in the MCP workflow tools: members declare `parent`, the validator reports the loop structure errors, and the
 * builder emits each loop as a box before its members, with member positions relative to the box.
 */
class WorkflowSpecLoopTest {

  private val table = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  private val source = ContinuumWorkflowModel.NodeData(
    description = "Starts a run", title = "Source", nodeModel = SOURCE,
    outputs = mapOf("out" to table)
  )

  private val step = ContinuumWorkflowModel.NodeData(
    description = "One step", title = "Step", nodeModel = STEP,
    inputs = mapOf("in" to table),
    outputs = mapOf("out" to table)
  )

  private val service: NodeExplorerService = mock<NodeExplorerService>().also { mockService ->
    whenever(mockService.getNodeMetadata(any())).thenReturn(null)
    whenever(mockService.getNodeMetadata(SOURCE)).thenReturn(source)
    whenever(mockService.getNodeMetadata(STEP)).thenReturn(step)
    whenever(mockService.getNodeMetadata(LoopNode.NODE_MODEL)).thenReturn(LoopNodeDescriptor.nodeData)
  }
  private val validator = WorkflowSpecValidator(service)
  private val builder = WorkflowSpecBuilder(validator = validator, nodeExplorerService = service)

  private companion object {
    const val SOURCE = "org.test.Source"
    const val STEP = "org.test.Step"
  }

  private fun node(key: String, nodeId: String, parent: String? = null, properties: Map<String, Any?>? = null) =
    McpWorkflowSpecNode(key = key, nodeId = nodeId, properties = properties, parent = parent)

  private fun edge(from: String, to: String) = McpWorkflowSpecEdge(from = from, fromPort = "out", to = to, toPort = "in")

  /** seed -> [fetch -> parse -> append] -> write, the example loop workflow. */
  private val exampleNodes = listOf(
    node(key = "seed", nodeId = SOURCE),
    node(key = "fetch", nodeId = STEP, parent = "L"),
    node(key = "parse", nodeId = STEP, parent = "L"),
    node(key = "append", nodeId = STEP, parent = "L"),
    node(key = "write", nodeId = STEP),
    node(key = "L", nodeId = LoopNode.NODE_MODEL, properties = mapOf("maxIterations" to 5))
  )
  private val exampleEdges = listOf(
    edge(from = "seed", to = "fetch"),
    edge(from = "fetch", to = "parse"),
    edge(from = "parse", to = "append"),
    edge(from = "append", to = "write")
  )

  private fun messages(errors: List<McpWorkflowIssue>) = errors.joinToString(separator = "\n") { issue -> issue.message }

  private fun assertInside(member: ContinuumWorkflowModel.Node, box: ContinuumWorkflowModel.Node) {
    assertEquals(box.id, member.parentNode)
    assertTrue(member.position.x >= 0.0 && member.position.y >= 0.0, "${member.id} at ${member.position}")
    assertTrue(member.position.x + member.width <= box.width, "${member.id} exceeds the width of ${box.id}")
    assertTrue(member.position.y + member.height <= box.height, "${member.id} exceeds the height of ${box.id}")
  }

  @Test
  fun `a spec with a loop and members declaring parent is valid`() {
    val result = validator.validate(name = "Paged fetch", nodes = exampleNodes, edges = exampleEdges)

    assertTrue(result.valid, messages(errors = result.errors))
    assertEquals(emptyList(), result.warnings.map { issue -> issue.message })
  }

  @Test
  fun `an unknown parent, an edge to a loop and a path through an outside node are errors`() {
    val unknownParent = validator.validate(
      name = "w",
      nodes = exampleNodes.map { specNode -> if (specNode.key == "parse") specNode.copy(parent = "Nope") else specNode },
      edges = exampleEdges
    )
    val edgeToLoop = validator.validate(
      name = "w",
      nodes = exampleNodes,
      edges = exampleEdges + edge(from = "seed", to = "L")
    )
    val throughOutside = validator.validate(
      name = "w",
      nodes = exampleNodes.map { specNode -> if (specNode.key == "parse") specNode.copy(parent = null) else specNode },
      edges = exampleEdges
    )

    // An unknown parent leaves 'parse' at the top level, so its edges also leave and re-enter 'L'.
    assertFalse(unknownParent.valid)
    assertEquals(
      listOf(
        "Node 'parse': its parent 'Nope' is not a node of this workflow. Set parent to the key of the loop the node is " +
          "inside, or remove it.",
        "The edges leave loop 'L' and come back into it through 'parse'. A node outside a loop cannot run between its " +
          "iterations; move 'parse' into 'L' or remove one of the edges."
      ),
      unknownParent.errors.map { issue -> issue.message }
    )
    assertEquals("parse", unknownParent.errors.first().node)
    assertEquals("seed.out -> L.in", edgeToLoop.errors.single().edge, messages(errors = edgeToLoop.errors))
    assertTrue(edgeToLoop.errors.single().message.contains("'L' is a loop. A loop has no ports"))
    assertEquals(
      "The edges leave loop 'L' and come back into it through 'parse'. A node outside a loop cannot run between its " +
        "iterations; move 'parse' into 'L' or remove one of the edges.",
      throughOutside.errors.single().message
    )
  }

  @Test
  fun `a loop's properties are merged over the defaults and checked against the Loop schema`() {
    val result = validator.validate(
      name = "w",
      nodes = exampleNodes.map { specNode ->
        if (specNode.key == "L") specNode.copy(properties = mapOf("maxIterations" to 0)) else specNode
      },
      edges = exampleEdges
    )

    val error = result.errors.single()
    assertEquals("L", error.node)
    assertEquals("maxIterations", error.property)
  }

  @Test
  fun `the built example lists the loop before its members, inside its box, with edges unchanged`() {
    val result = builder.build(name = "Paged fetch", nodes = exampleNodes, edges = exampleEdges)

    assertTrue(result.valid, messages(errors = result.errors))
    val workflow = result.workflow!!
    assertEquals(listOf("seed", "L", "fetch", "parse", "append", "write"), workflow.nodes.map { node -> node.id })
    val nodesById = workflow.nodes.associateBy { node -> node.id }
    val loop = nodesById.getValue("L")
    assertEquals(LoopNode.NODE_TYPE, loop.type)
    assertNull(loop.parentNode)
    assertEquals(false, loop.data.collapsed)
    assertEquals(LoopNode.NODE_MODEL, loop.data.nodeModel)
    assertEquals(LoopNodeDescriptor.defaultProperties + mapOf("maxIterations" to 5), loop.data.properties)
    listOf("fetch", "parse", "append").forEach { memberId ->
      val member = nodesById.getValue(memberId)
      assertEquals("BaseNode", member.type)
      assertNull(member.data.collapsed)
      assertInside(member = member, box = loop)
    }
    assertNull(nodesById.getValue("write").parentNode)
    assertEquals(
      listOf("fetch", "parse", "append").map { memberId -> nodesById.getValue(memberId).position.x },
      listOf(40.0, 370.0, 700.0),
      "members are laid out left to right by depth inside the box"
    )
    assertEquals(
      exampleEdges.map { specEdge -> "reactflow__edge-${specEdge.from}out-${specEdge.to}in" },
      workflow.edges.map { workflowEdge -> workflowEdge.id }
    )
  }

  @Test
  fun `the loop box grows with its members and pushes the next column right`() {
    val workflow = builder.build(name = "w", nodes = exampleNodes, edges = exampleEdges).workflow!!
    val nodesById = workflow.nodes.associateBy { node -> node.id }
    val loop = nodesById.getValue("L")
    val write = nodesById.getValue("write")

    // 40 px padding, members at 0 / 330 / 660 inside the content, the last one 224 px wide, 40 px padding
    assertEquals(40 + 660 + WorkflowSpecLayout.NODE_WIDTH + 40, loop.width)
    assertEquals(WorkflowSpecLayout.LOOP_MIN_HEIGHT, loop.height)
    assertEquals(330.0, loop.position.x)
    assertTrue(write.position.x >= loop.position.x + loop.width, "write at ${write.position.x}, loop ends at ${loop.position.x + loop.width}")
    assertEquals(loop.position.x + 40.0, nodesById.getValue("fetch").positionAbsolute!!.x)
    assertEquals(loop.position.y + 60.0, nodesById.getValue("fetch").positionAbsolute!!.y)
  }

  @Test
  fun `a nested loop is contained in its parent box and both come before their members`() {
    val nodes = listOf(
      node(key = "seed", nodeId = SOURCE),
      node(key = "outer", nodeId = LoopNode.NODE_MODEL),
      node(key = "a", nodeId = STEP, parent = "outer"),
      node(key = "inner", nodeId = LoopNode.NODE_MODEL, parent = "outer"),
      node(key = "b", nodeId = STEP, parent = "inner"),
      node(key = "c", nodeId = STEP, parent = "inner"),
      node(key = "d", nodeId = STEP, parent = "outer")
    )
    val edges = listOf(
      edge(from = "seed", to = "a"),
      edge(from = "a", to = "b"),
      edge(from = "b", to = "c"),
      edge(from = "c", to = "d")
    )

    val result = builder.build(name = "nested", nodes = nodes, edges = edges)

    assertTrue(result.valid, messages(errors = result.errors))
    val workflow = result.workflow!!
    assertEquals(listOf("seed", "outer", "a", "inner", "b", "c", "d"), workflow.nodes.map { node -> node.id })
    val nodesById = workflow.nodes.associateBy { node -> node.id }
    val outer = nodesById.getValue("outer")
    val inner = nodesById.getValue("inner")
    assertInside(member = nodesById.getValue("a"), box = outer)
    assertInside(member = inner, box = outer)
    assertInside(member = nodesById.getValue("d"), box = outer)
    assertInside(member = nodesById.getValue("b"), box = inner)
    assertInside(member = nodesById.getValue("c"), box = inner)
    assertEquals(LoopNode.NODE_TYPE, inner.type)
    assertTrue(nodesById.getValue("d").position.x >= inner.position.x + inner.width, "d is placed after the inner box")
  }

  @Test
  fun `a loop without members gets the default box size`() {
    val nodes = listOf(node(key = "seed", nodeId = SOURCE), node(key = "L", nodeId = LoopNode.NODE_MODEL))

    val result = builder.build(name = "w", nodes = nodes, edges = emptyList())

    val loop = result.workflow!!.nodes.single { node -> node.id == "L" }
    assertEquals(WorkflowSpecLayout.LOOP_MIN_WIDTH, loop.width)
    assertEquals(WorkflowSpecLayout.LOOP_MIN_HEIGHT, loop.height)
    assertTrue(result.warnings.any { issue -> issue.message == "Loop 'L' has no nodes inside it." }, result.warnings.toString())
  }

  @Test
  fun `there are still exactly six MCP tools and both workflow tools describe loops`() {
    val toolClasses = listOf(ContinuumNodeMcpTools::class.java, ContinuumWorkflowMcpTools::class.java)
    val tools = toolClasses.flatMap { toolClass ->
      toolClass.methods.mapNotNull { method -> method.getAnnotation(McpTool::class.java) }
    }

    assertEquals(
      setOf(
        "listContinuumNodeCategories",
        "searchContinuumNodes",
        "getContinuumNodeMetadata",
        "getContinuumNodeDocumentation",
        "validateContinuumWorkflow",
        "buildContinuumWorkflow"
      ),
      tools.map { tool -> tool.name }.toSet()
    )
    assertEquals(6, tools.size)
    tools.filter { tool -> tool.name.endsWith("ContinuumWorkflow") }.forEach { tool ->
      assertTrue(tool.description.contains(LoopNode.NODE_MODEL), "${tool.name} must name the loop nodeId")
      assertTrue(tool.description.contains("set parent to its key"), "${tool.name} must explain parent")
      assertTrue(tool.description.contains("Edges never name a loop"), "${tool.name} must say edges never name a loop")
      assertTrue(tool.description.contains("properties.condition is a CEL expression over vars"), tool.name)
      assertTrue(tool.description.contains("vars[\"name\"]"), "${tool.name} must teach vars[\"name\"]")
      assertTrue(tool.description.contains("onNonBooleanResult"), "${tool.name} must name onNonBooleanResult")
      assertTrue(tool.description.contains("validateContinuumWorkflow is authoritative"), tool.name)
    }
  }

  private val rejectedConditions: Map<String, String> = mapOf(
    "now() > 1" to "does not type-check",
    "foo == 1" to "does not type-check",
    "vars[\"p\"] + 1" to "does not return true or false",
    "vars[\"a\"] ==" to "is not a valid CEL expression",
    "vars[\"s\"].matches(\"(\")" to "contains an invalid regex, timestamp or duration literal",
    "cel.bind(a, 1, cel.bind(a, a + a, a > 0))" to "binds a name that is already bound in an enclosing scope",
    "vars[\"a\"] == " + "(".repeat(33) + "1" + ")".repeat(33) to "nests brackets deeper than 32 levels",
    // The schema's maxLength reports it (one error, from the schema), so the condition validator is not asked
    "x".repeat(2049) to "must be at most 2,048 characters long"
  )

  private val acceptedConditions = listOf(
    "vars[\"page\"] < 10 && vars[\"hasMore\"]",
    "\"nextCursor\" in vars",
    "vars[\"items\"].exists(i, i > 2)",
    "vars[\"name\"].lowerAscii() == \"x\"",
    "math.greatest(vars[\"a\"], 1) > 0",
    "vars[?\"r\"].orValue(0) < 3",
    "timestamp(vars[\"ts\"]) > timestamp(\"2026-01-01T00:00:00Z\")"
  )

  private fun withCondition(condition: String) =
    exampleNodes.map { specNode -> if (specNode.key == "L") specNode.copy(properties = mapOf("condition" to condition)) else specNode }

  @Test
  fun `each rejected condition is exactly one error on the loop's condition from both workflow tools`() {
    rejectedConditions.forEach { (condition, reason) ->
      val validation = validator.validate(name = "paging", nodes = withCondition(condition = condition), edges = exampleEdges)
      val build = builder.build(name = "paging", nodes = withCondition(condition = condition), edges = exampleEdges)

      listOf(validation.errors, build.errors).forEach { errors ->
        val conditionErrors = errors.filter { issue -> issue.node == "L" && issue.property == "condition" }
        assertEquals(1, conditionErrors.size, "'${condition.take(40)}': ${messages(errors = errors)}")
        assertEquals(1, errors.size, "no other error for '${condition.take(40)}': ${messages(errors = errors)}")
        assertTrue(conditionErrors.single().message.contains(reason), conditionErrors.single().message)
      }
      assertFalse(build.valid)
      assertNull(build.workflow)
    }
  }

  @Test
  fun `the accepted full CEL conditions give no condition issue`() {
    acceptedConditions.forEach { condition ->
      val validation = validator.validate(name = "paging", nodes = withCondition(condition = condition), edges = exampleEdges)

      assertTrue(validation.valid, "$condition: ${messages(errors = validation.errors)}")
      assertTrue(validation.warnings.none { issue -> issue.property == "condition" }, "$condition: ${validation.warnings}")
    }
  }

  @Test
  fun `a condition that reads no flow variable is one warning`() {
    val validation = validator.validate(name = "paging", nodes = withCondition(condition = "true"), edges = exampleEdges)

    assertTrue(validation.valid)
    val conditionWarnings = validation.warnings.filter { issue -> issue.property == "condition" }
    assertEquals(1, conditionWarnings.size, validation.warnings.toString())
    assertTrue(conditionWarnings.single().message.startsWith("Loop 'L': the condition reads no flow variable"))
  }
}
