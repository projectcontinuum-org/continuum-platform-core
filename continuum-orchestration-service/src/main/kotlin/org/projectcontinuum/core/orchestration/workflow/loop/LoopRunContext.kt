package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData

/**
 * The mutable run data the loop collaborators read and change: the workflow model (statuses, progress, edge data),
 * the outputs and errors of finished nodes, and the way a node's status is set (the status reporter, which also
 * animates edges). Built once per run by the DAG executor.
 */
class LoopRunContext(
  val workflow: ContinuumWorkflowModel,
  val topology: LoopTopology,
  val nodeToOutputsMap: MutableMap<String, Map<String, PortData>>,
  val nodeErrorsMap: MutableMap<String, Map<String, PortData>>,
  val setNodeStatus: (node: ContinuumWorkflowModel.Node, status: ContinuumWorkflowModel.NodeStatus) -> Unit
) {
  private val nodesById: Map<String, ContinuumWorkflowModel.Node> = workflow.nodes.associateBy { node -> node.id }

  fun node(nodeId: String): ContinuumWorkflowModel.Node = nodesById.getValue(nodeId)
}
