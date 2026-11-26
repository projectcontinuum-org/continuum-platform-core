package org.projectcontinuum.core.orchestration.workflow.fixtures

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor

/**
 * Builds small workflow models with loops in code, for the pure loop tests. Every plain node has one input `in` and one
 * output `out`; [source] nodes have only `out`. Edges are `out -> in`, with id `<source>-<target>`.
 */
class LoopWorkflowBuilder {

  private val nodes = mutableListOf<ContinuumWorkflowModel.Node>()
  private val edges = mutableListOf<ContinuumWorkflowModel.Edge>()

  private val port = ContinuumWorkflowModel.NodePort(name = "table", contentType = "text/plain")

  fun source(id: String, parent: String? = null) = apply {
    nodes += node(id = id, parent = parent, inputs = null, nodeModel = "test.node.Source")
  }

  fun step(id: String, parent: String? = null) = apply {
    nodes += node(id = id, parent = parent, inputs = mapOf("in" to port), nodeModel = "test.node.Step")
  }

  fun loop(id: String, parent: String? = null) = apply {
    nodes += ContinuumWorkflowModel.Node(
      id = id,
      type = LoopNode.NODE_TYPE,
      position = ContinuumWorkflowModel.Position(x = 0.0, y = 0.0),
      data = LoopNodeDescriptor.nodeData.copy(collapsed = false),
      width = 520,
      height = 320,
      selected = false,
      parentNode = parent
    )
  }

  fun edge(from: String, to: String) = apply {
    edges += ContinuumWorkflowModel.Edge(
      id = "$from-$to",
      source = from,
      target = to,
      sourceHandle = "out",
      targetHandle = "in"
    )
  }

  fun build(): ContinuumWorkflowModel =
    ContinuumWorkflowModel(id = "loop-test-workflow", name = "/test-workflows/loop.cwf", nodes = nodes.toList(), edges = edges.toList())

  private fun node(
    id: String,
    parent: String?,
    inputs: Map<String, ContinuumWorkflowModel.NodePort>?,
    nodeModel: String
  ) = ContinuumWorkflowModel.Node(
    id = id,
    type = "BaseNode",
    position = ContinuumWorkflowModel.Position(x = 0.0, y = 0.0),
    data = ContinuumWorkflowModel.NodeData(
      id = nodeModel,
      description = "",
      title = id,
      nodeModel = nodeModel,
      inputs = inputs,
      outputs = mapOf("out" to port)
    ),
    width = 224,
    height = 100,
    selected = false,
    parentNode = parent
  )
}
