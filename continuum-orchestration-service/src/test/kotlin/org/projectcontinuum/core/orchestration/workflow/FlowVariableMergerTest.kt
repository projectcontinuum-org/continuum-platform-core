package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.utils.FlowVariables
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowVariableMergerTest {

  private val textPort = ContinuumWorkflowModel.NodePort(name = "table", contentType = "text/plain")
  private val dataPort = PortData(status = PortDataStatus.SUCCESS, contentType = "text/plain", tableSpec = emptyList(), data = "x")

  private fun graphNode(id: String, inputPortIds: List<String> = emptyList()) = ContinuumWorkflowModel.Node(
    id = id,
    type = "BaseNode",
    position = ContinuumWorkflowModel.Position(0.0, 0.0),
    data = ContinuumWorkflowModel.NodeData(
      description = id,
      title = id,
      nodeModel = "test.node.$id",
      inputs = inputPortIds.associateWith { textPort }.ifEmpty { null },
      outputs = mapOf("out" to textPort)
    ),
    width = 1,
    height = 1,
    selected = false
  )

  private fun graphEdge(source: String, target: String, targetHandle: String = "in") = ContinuumWorkflowModel.Edge(
    id = "$source-$target-$targetHandle",
    source = source,
    target = target,
    sourceHandle = "out",
    targetHandle = targetHandle
  )

  /** Completed outputs of a node: one data port, plus `$variable` when it set any. */
  private fun completed(ownVariables: Map<String, Any> = emptyMap()): Map<String, PortData> {
    if (ownVariables.isEmpty()) return mapOf("out" to dataPort)
    return mapOf("out" to dataPort, NodeOutputSystemPort.VARIABLE.key to FlowVariables.toPortData(ownVariables))
  }

  private fun collect(
    workflow: ContinuumWorkflowModel,
    nodeId: String,
    outputs: Map<String, Map<String, PortData>>
  ) = FlowVariableMerger.collectInputVariables(
    continuumWorkflow = workflow,
    node = workflow.nodes.single { it.id == nodeId },
    nodeToOutputsMap = outputs
  )

  private val chain = ContinuumWorkflowModel(
    id = "chain",
    name = "chain",
    nodes = listOf(graphNode("A"), graphNode("B", listOf("in")), graphNode("C", listOf("in"))),
    edges = listOf(graphEdge(source = "A", target = "B"), graphEdge(source = "B", target = "C"))
  )

  /** A feeds B and C; B feeds D's first input port, C feeds D's second. */
  private val diamond = ContinuumWorkflowModel(
    id = "diamond",
    name = "diamond",
    nodes = listOf(
      graphNode("A"),
      graphNode("B", listOf("in")),
      graphNode("C", listOf("in")),
      graphNode("D", listOf("first", "second"))
    ),
    edges = listOf(
      // Listed second-port-first so the result cannot depend on edge list order.
      graphEdge(source = "C", target = "D", targetHandle = "second"),
      graphEdge(source = "A", target = "B"),
      graphEdge(source = "A", target = "C"),
      graphEdge(source = "B", target = "D", targetHandle = "first")
    )
  )

  @Test
  fun `a grandparent's variable reaches the grandchild through a parent that sets nothing`() {
    val result = collect(workflow = chain, nodeId = "C", outputs = mapOf("A" to completed(mapOf("x" to 1)), "B" to completed()))

    assertEquals(mapOf("x" to 1), result.variables)
    assertEquals(emptyList(), result.conflicts)
  }

  @Test
  fun `a nearer ancestor overrides an older value and adds its own`() {
    val outputs = mapOf(
      "A" to completed(mapOf("x" to 1, "keep" to "a")),
      "B" to completed(mapOf("x" to 10, "y" to 2))
    )

    val result = collect(workflow = chain, nodeId = "C", outputs = outputs)

    assertEquals(mapOf("x" to 10, "keep" to "a", "y" to 2), result.variables)
  }

  @Test
  fun `a root node inherits nothing`() {
    val result = collect(workflow = chain, nodeId = "A", outputs = emptyMap())

    assertEquals(emptyMap(), result.variables)
  }

  @Test
  fun `fan-in takes the value from the lowest input port and reports the conflict`() {
    val outputs = mapOf(
      "A" to completed(),
      "B" to completed(mapOf("k" to "from-B")),
      "C" to completed(mapOf("k" to "from-C"))
    )

    val result = collect(workflow = diamond, nodeId = "D", outputs = outputs)

    assertEquals(mapOf("k" to "from-B"), result.variables)
    assertEquals(
      listOf(FlowVariableMerger.Conflict(key = "k", targetNodeId = "D", keptFromNodeId = "B", ignoredFromNodeId = "C")),
      result.conflicts
    )
  }

  @Test
  fun `the same ancestor value arriving by two paths merges without a conflict`() {
    val outputs = mapOf(
      "A" to completed(mapOf("shared" to listOf(1, 2), "x" to 1)),
      "B" to completed(mapOf("b" to true)),
      "C" to completed(mapOf("c" to true))
    )

    val result = collect(workflow = diamond, nodeId = "D", outputs = outputs)

    assertEquals(mapOf("shared" to listOf(1, 2), "x" to 1, "b" to true, "c" to true), result.variables)
    assertEquals(emptyList(), result.conflicts)
  }

  @Test
  fun `a branch override of an ancestor value wins only on its own port order`() {
    // C (second port) overrides A's x; B (first port) passes A's x through unchanged, so B's value wins.
    val outputs = mapOf(
      "A" to completed(mapOf("x" to 1)),
      "B" to completed(),
      "C" to completed(mapOf("x" to 2))
    )

    val result = collect(workflow = diamond, nodeId = "D", outputs = outputs)

    assertEquals(mapOf("x" to 1), result.variables)
    assertEquals(listOf("C"), result.conflicts.map { it.ignoredFromNodeId })
  }

  @Test
  fun `edges into undeclared ports are ordered by target handle then source`() {
    val undeclared = ContinuumWorkflowModel(
      id = "undeclared",
      name = "undeclared",
      nodes = listOf(graphNode("P"), graphNode("Q"), graphNode("T")),
      edges = listOf(
        graphEdge(source = "Q", target = "T", targetHandle = "a"),
        graphEdge(source = "P", target = "T", targetHandle = "b")
      )
    )
    val outputs = mapOf("P" to completed(mapOf("k" to "P")), "Q" to completed(mapOf("k" to "Q")))

    val result = collect(workflow = undeclared, nodeId = "T", outputs = outputs)

    assertEquals(mapOf("k" to "Q"), result.variables)
  }

  @Test
  fun `only conflicts at the target node are reported`() {
    // D merges B and C with a conflict; E below D must not report D's conflict again.
    val extended = diamond.copy(
      nodes = diamond.nodes + graphNode("E", listOf("in")),
      edges = diamond.edges + graphEdge(source = "D", target = "E")
    )
    val outputs = mapOf(
      "A" to completed(),
      "B" to completed(mapOf("k" to "from-B")),
      "C" to completed(mapOf("k" to "from-C")),
      "D" to completed()
    )

    val result = collect(workflow = extended, nodeId = "E", outputs = outputs)

    assertEquals(mapOf("k" to "from-B"), result.variables)
    assertEquals(emptyList(), result.conflicts)
  }

  @Test
  fun `no variables anywhere gives an empty result`() {
    val outputs = mapOf("A" to completed(), "B" to completed(), "C" to completed())

    val result = collect(workflow = diamond, nodeId = "D", outputs = outputs)

    assertEquals(emptyMap(), result.variables)
    assertEquals(emptyList(), result.conflicts)
  }
}
