package org.projectcontinuum.core.commons.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor

/** Loop structure rules, loop property checks and the collapsed-graph cycle check of [WorkflowValidator]. */
class WorkflowValidatorLoopStructureTest {

  private val table = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  private val step = ContinuumWorkflowModel.NodeData(
    description = "",
    title = "Step",
    nodeModel = STEP,
    inputs = mapOf("in" to table),
    outputs = mapOf("out" to table)
  )

  private val source = ContinuumWorkflowModel.NodeData(
    description = "",
    title = "Source",
    nodeModel = SOURCE,
    outputs = mapOf("out" to table)
  )

  private val validator = WorkflowValidator { nodeModel ->
    when (nodeModel) {
      STEP -> step
      SOURCE -> source
      LoopNode.NODE_MODEL -> LoopNodeDescriptor.nodeData
      else -> null
    }
  }

  private companion object {
    const val STEP = "org.test.Step"
    const val SOURCE = "org.test.Source"
  }

  private fun loop(key: String, parent: String? = null, properties: Map<String, Any?> = LoopNodeDescriptor.defaultProperties) =
    GraphNode(key = key, nodeId = LoopNode.NODE_MODEL, properties = properties, type = LoopNode.NODE_TYPE, parent = parent)

  private fun step(key: String, parent: String? = null, type: String? = "BaseNode") =
    GraphNode(key = key, nodeId = STEP, properties = emptyMap(), type = type, parent = parent)

  private fun source(key: String, parent: String? = null) =
    GraphNode(key = key, nodeId = SOURCE, properties = emptyMap(), type = "BaseNode", parent = parent)

  private fun edge(from: String, to: String) = GraphEdge(from = from, fromPort = "out", to = to, toPort = "in")

  private fun validate(nodes: List<GraphNode>, edges: List<GraphEdge>, mode: ValidationMode = ValidationMode.RUN) =
    validator.validate(nodes = nodes, edges = edges, mode = mode)

  private fun assertValid(result: WorkflowValidationResult) =
    assertTrue(result.valid, "expected valid, errors: ${result.errors.map { issue -> issue.message }}")

  private fun assertSingleError(result: WorkflowValidationResult): WorkflowIssue {
    assertEquals(1, result.errors.size, "expected one error: ${result.errors.map { issue -> issue.message }}")
    return result.errors.single()
  }

  /** The paging example: seed, then fetch, parse and append inside the loop, then write. */
  private val exampleNodes = listOf(
    source(key = "seed"),
    loop(key = "L"),
    step(key = "fetch", parent = "L"),
    step(key = "parse", parent = "L"),
    step(key = "append", parent = "L"),
    step(key = "write")
  )
  private val exampleEdges = listOf(
    edge(from = "seed", to = "fetch"),
    edge(from = "fetch", to = "parse"),
    edge(from = "parse", to = "append"),
    edge(from = "append", to = "write")
  )

  @Test
  fun `the example loop workflow is valid in both modes and the loop is never 'not connected'`() {
    ValidationMode.entries.forEach { mode ->
      val result = validate(nodes = exampleNodes, edges = exampleEdges, mode = mode)

      assertValid(result = result)
      assertTrue(result.warnings.none { warning -> warning.node == "L" }, "no warning on the loop: ${result.warnings}")
    }
  }

  @Test
  fun `a loop nested in a loop is valid`() {
    val nodes = listOf(
      source(key = "seed"),
      loop(key = "outer"),
      step(key = "a", parent = "outer"),
      loop(key = "inner", parent = "outer"),
      step(key = "b", parent = "inner"),
      step(key = "c", parent = "inner")
    )
    val edges = listOf(edge(from = "seed", to = "a"), edge(from = "a", to = "b"), edge(from = "b", to = "c"))

    assertValid(result = validate(nodes = nodes, edges = edges))
  }

  @Test
  fun `a parent chain that loops back is one error naming the node`() {
    val nodes = listOf(loop(key = "L1", parent = "L2"), loop(key = "L2", parent = "L1"), step(key = "x", parent = "L1"))

    val error = assertSingleError(result = validate(nodes = nodes, edges = emptyList()))

    assertEquals("Node 'L1': its parent chain 'L1' -> 'L2' -> 'L1' is a cycle. A loop cannot be inside itself.", error.message)
    assertEquals("L1", error.node)
  }

  @Test
  fun `a loop that is its own parent is a cycle`() {
    val error = assertSingleError(result = validate(nodes = listOf(loop(key = "L", parent = "L")), edges = emptyList()))

    assertEquals("Node 'L': its parent chain 'L' -> 'L' is a cycle. A loop cannot be inside itself.", error.message)
  }

  @Test
  fun `an unknown parent is one error naming the node`() {
    val error = assertSingleError(result = validate(nodes = listOf(step(key = "x", parent = "missing")), edges = emptyList()))

    assertEquals(
      "Node 'x': its parent 'missing' is not a node of this workflow. Set parent to the key of the loop the node is " +
        "inside, or remove it.",
      error.message
    )
    assertEquals("x", error.node)
  }

  @Test
  fun `a parent that is not a loop is one error naming the node`() {
    val nodes = listOf(source(key = "s"), step(key = "x", parent = "s"))

    val error = assertSingleError(result = validate(nodes = nodes, edges = listOf(edge(from = "s", to = "x"))))

    assertEquals(
      "Node 'x': its parent 's' is not a loop. Only a loop (nodeId 'org.projectcontinuum.core.builtin.Loop') can contain nodes.",
      error.message
    )
  }

  @Test
  fun `type Loop with another nodeId is one error naming the node`() {
    val error = assertSingleError(result = validate(nodes = listOf(step(key = "x", type = "Loop")), edges = emptyList()))

    assertEquals(
      "Node 'x': type 'Loop' is only for the built-in loop (nodeId 'org.projectcontinuum.core.builtin.Loop'), but this " +
        "node has nodeId 'org.test.Step'. Use type 'BaseNode' for registered nodes.",
      error.message
    )
    assertEquals("x", error.node)
  }

  @Test
  fun `the loop nodeId with another type is one error naming the node`() {
    val mistypedLoop = loop(key = "L").copy(type = "BaseNode")

    val result = validate(nodes = listOf(mistypedLoop, step(key = "x", parent = "L")), edges = emptyList())
    val error = assertSingleError(result = result)

    assertEquals(
      "Node 'L': the built-in loop (nodeId 'org.projectcontinuum.core.builtin.Loop') needs type 'Loop', not 'BaseNode'.",
      error.message
    )
  }

  @Test
  fun `no type at all is not checked, as in MCP specs`() {
    val nodes = listOf(loop(key = "L").copy(type = null), step(key = "x", parent = "L", type = null))

    assertValid(result = validate(nodes = nodes, edges = emptyList(), mode = ValidationMode.AUTHORING))
  }

  @Test
  fun `an edge from or to a loop is one error naming the edge and no port error`() {
    val nodes = listOf(source(key = "s"), loop(key = "L"), step(key = "x", parent = "L"), step(key = "after"))
    val edgeIntoLoop = edge(from = "s", to = "L")
    val edgeOutOfLoop = edge(from = "L", to = "after")

    val result = validate(nodes = nodes, edges = listOf(edgeIntoLoop, edgeOutOfLoop))

    assertEquals(
      listOf(
        "Edge s.out -> L.in: 'L' is a loop. A loop has no ports; connect the nodes inside it instead.",
        "Edge L.out -> after.in: 'L' is a loop. A loop has no ports; connect the nodes inside it instead."
      ),
      result.errors.map { issue -> issue.message }
    )
    assertEquals(listOf("s.out -> L.in", "L.out -> after.in"), result.errors.map { issue -> issue.edge })
  }

  @Test
  fun `leaving a loop and coming back through an outside node is rejected naming the loop and the node`() {
    val nodes = listOf(source(key = "seed"), loop(key = "L"), step(key = "X1", parent = "L"), step(key = "Y"), step(key = "X2", parent = "L"))
    val edges = listOf(edge(from = "seed", to = "X1"), edge(from = "X1", to = "Y"), edge(from = "Y", to = "X2"))

    val error = assertSingleError(result = validate(nodes = nodes, edges = edges))

    assertEquals(
      "The edges leave loop 'L' and come back into it through 'Y'. A node outside a loop cannot run between its " +
        "iterations; move 'Y' into 'L' or remove one of the edges.",
      error.message
    )
    assertEquals("L", error.node)
  }

  @Test
  fun `entering and leaving a loop once is accepted`() {
    val nodes = listOf(source(key = "A"), loop(key = "L"), step(key = "X1", parent = "L"), step(key = "X2", parent = "L"), step(key = "B"))
    val edges = listOf(edge(from = "A", to = "X1"), edge(from = "X1", to = "X2"), edge(from = "X2", to = "B"))

    assertValid(result = validate(nodes = nodes, edges = edges))
  }

  @Test
  fun `a collapsed cycle inside an outer loop is found in the outer scope`() {
    val nodes = listOf(
      source(key = "seed"),
      loop(key = "outer"),
      loop(key = "inner", parent = "outer"),
      step(key = "i1", parent = "inner"),
      step(key = "middle", parent = "outer"),
      step(key = "i2", parent = "inner")
    )
    val edges = listOf(edge(from = "seed", to = "i1"), edge(from = "i1", to = "middle"), edge(from = "middle", to = "i2"))

    val error = assertSingleError(result = validate(nodes = nodes, edges = edges))

    assertTrue(error.message.startsWith("The edges leave loop 'inner' and come back into it through 'middle'."), error.message)
  }

  @Test
  fun `a real cycle keeps the plain cycle message and no collapsed message`() {
    val nodes = listOf(loop(key = "L"), step(key = "a", parent = "L"), step(key = "b", parent = "L"))
    val edges = listOf(edge(from = "a", to = "b"), edge(from = "b", to = "a"))

    val error = assertSingleError(result = validate(nodes = nodes, edges = edges))

    assertEquals("The edges contain a cycle through nodes 'a', 'b'. A workflow must be a directed acyclic graph.", error.message)
  }

  @Test
  fun `a loop with no members is a warning, not an error`() {
    val result = validate(nodes = listOf(source(key = "s"), loop(key = "L")), edges = emptyList())

    assertValid(result = result)
    assertTrue(result.warnings.any { warning -> warning.message == "Loop 'L' has no nodes inside it." && warning.node == "L" })
    assertTrue(result.warnings.none { warning -> warning.message == "Node 'L' is not connected to any other node." })
  }

  @Test
  fun `bad loop properties give one error each naming the loop and the property in both modes`() {
    val badValues = listOf(
      "maxIterations" to 0,
      "maxIterations" to 10_001,
      "onLimit" to "ignore",
      "check" to "during",
      "condition" to "",
      "condition" to "x".repeat(2049)
    )
    ValidationMode.entries.forEach { mode ->
      badValues.forEach { (property, value) ->
        val properties = LoopNodeDescriptor.defaultProperties + (property to value)
        val nodes = listOf(loop(key = "L", properties = properties), step(key = "x", parent = "L"))

        val result = validate(nodes = nodes, edges = emptyList(), mode = mode)

        assertEquals(1, result.errors.size, "$mode $property=$value: ${result.errors.map { issue -> issue.message }}")
        assertEquals("L", result.errors.single().node)
        assertEquals(property, result.errors.single().property)
      }
    }
  }

  @Test
  fun `a loop-free workflow gives exactly the issues it gave before loops existed`() {
    val nodes = listOf(source(key = "s"), step(key = "a"), step(key = "lonely"))
    val edges = listOf(edge(from = "s", to = "a"), edge(from = "a", to = "missing"))

    val result = validate(nodes = nodes, edges = edges)

    assertEquals(
      listOf("Edge a.out -> missing.in: target node key 'missing' does not exist. Node keys: s, a, lonely."),
      result.errors.map { issue -> issue.message }
    )
    assertEquals(
      listOf(
        "Node 'lonely' is not connected to any other node.",
        "Node 'lonely': input port 'in' has no incoming edge. The node is skipped at run time, so nodes that depend on it do not run."
      ),
      result.warnings.map { issue -> issue.message }
    )
  }

  @Test
  fun `a maxIterations over the run-history budget is a warning on the loop, not an error`() {
    val nodes = exampleNodes.map { node ->
      if (node.key == "L") loop(key = "L", properties = LoopNodeDescriptor.defaultProperties + ("maxIterations" to 10_000)) else node
    }

    val result = validate(nodes = nodes, edges = exampleEdges)

    assertValid(result = result)
    val budgetWarning = result.warnings.single { warning -> warning.property == "maxIterations" }
    assertEquals("L", budgetWarning.node)
    assertTrue(budgetWarning.message.startsWith("Loop 'L': maxIterations 10000 x 3 nodes inside may exceed the run history limit"))
    assertTrue(validate(nodes = exampleNodes, edges = exampleEdges).warnings.none { warning -> warning.property == "maxIterations" })
  }
}
