package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.EdgeStatus
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.model.PortData

/**
 * Pure readiness/skip decisions for [WorkflowDagExecutor]. Nothing here mutates the model or
 * the output maps, so decisions depend only on the workflow model and the two output maps
 * (safe under Temporal replay).
 */
internal object NodeReadiness {

  /** A node that has not started, finished, failed or been skipped. */
  private fun isUntouched(
    node: ContinuumWorkflowModel.Node,
    nodeOutputMap: Map<String, Map<String, PortData>>,
    nodeErrorsMap: Map<String, Map<String, PortData>>
  ) = node.data.status == null &&
      !nodeOutputMap.containsKey(node.id) &&
      !nodeErrorsMap.containsKey(node.id)

  private fun hasUnconnectedInput(
    continuumWorkflow: ContinuumWorkflowModel,
    node: ContinuumWorkflowModel.Node
  ): Boolean {
    val parentEdges = continuumWorkflow.getParentEdges(node)
    return node.data.inputs?.any { input -> parentEdges.none { it.targetHandle == input.key } } ?: false
  }

  /** Returns the edge's status, or `null` while its source has not finished. */
  private fun edgeStatus(
    edge: ContinuumWorkflowModel.Edge,
    nodesById: Map<String, ContinuumWorkflowModel.Node>,
    nodeOutputMap: Map<String, Map<String, PortData>>,
    nodeErrorsMap: Map<String, Map<String, PortData>>,
    nodeIdsSkippedInThisPass: Set<String> = emptySet()
  ): EdgeStatus? {
    val sourceStatus = nodesById[edge.source]?.data?.status
    val sourceOutputs = nodeOutputMap[edge.source]
    return when {
      nodeErrorsMap.containsKey(edge.source) ||
          sourceStatus == NodeStatus.FAILED ||
          sourceStatus == NodeStatus.SKIPPED ||
          edge.source in nodeIdsSkippedInThisPass -> EdgeStatus.BROKEN
      sourceOutputs == null -> null
      sourceOutputs.containsKey(edge.sourceHandle) -> EdgeStatus.DELIVERED
      else -> EdgeStatus.BROKEN
    }
  }

  /**
   * Extension point: whether [node] can still run when its incoming [edge] is broken (the source
   * failed, was skipped, or did not produce the port). Always false for now. Branch conditions
   * (task 10) can return true here; such a node then runs without data on that input port.
   */
  @Suppress("UNUSED_PARAMETER")
  private fun toleratesBrokenEdge(
    node: ContinuumWorkflowModel.Node,
    edge: ContinuumWorkflowModel.Edge
  ) = false

  /**
   * Returns the untouched nodes whose every incoming edge has delivered its port (or is broken
   * and tolerated by [toleratesBrokenEdge]), in `continuumWorkflow.nodes` list order.
   */
  fun getReadyNodes(
    continuumWorkflow: ContinuumWorkflowModel,
    nodeOutputMap: Map<String, Map<String, PortData>>,
    nodeErrorsMap: Map<String, Map<String, PortData>>
  ): List<ContinuumWorkflowModel.Node> {
    val nodesById = continuumWorkflow.nodes.associateBy { it.id }
    return continuumWorkflow.nodes.filter { node ->
      if (!isUntouched(node = node, nodeOutputMap = nodeOutputMap, nodeErrorsMap = nodeErrorsMap)) {
        return@filter false
      }
      if (hasUnconnectedInput(continuumWorkflow = continuumWorkflow, node = node)) return@filter false
      continuumWorkflow.getParentEdges(node).all { edge ->
        when (edgeStatus(edge = edge, nodesById = nodesById, nodeOutputMap = nodeOutputMap, nodeErrorsMap = nodeErrorsMap)) {
          EdgeStatus.DELIVERED -> true
          EdgeStatus.BROKEN -> toleratesBrokenEdge(node = node, edge = edge)
          null -> false
        }
      }
    }
  }

  /**
   * Returns the nodes that can never run: untouched nodes with an unconnected declared input, or
   * with a broken incoming edge that [toleratesBrokenEdge] does not accept. Applied transitively,
   * including nodes found skippable in this same pass. Result is in `continuumWorkflow.nodes` order.
   */
  fun getNodesToSkip(
    continuumWorkflow: ContinuumWorkflowModel,
    nodeOutputMap: Map<String, Map<String, PortData>>,
    nodeErrorsMap: Map<String, Map<String, PortData>>
  ): List<ContinuumWorkflowModel.Node> {
    val nodesById = continuumWorkflow.nodes.associateBy { it.id }
    val skippedNodeIds = mutableSetOf<String>()

    fun cannotRun(node: ContinuumWorkflowModel.Node): Boolean {
      if (hasUnconnectedInput(continuumWorkflow = continuumWorkflow, node = node)) return true
      return continuumWorkflow.getParentEdges(node).any { edge ->
        val status = edgeStatus(
          edge = edge,
          nodesById = nodesById,
          nodeOutputMap = nodeOutputMap,
          nodeErrorsMap = nodeErrorsMap,
          nodeIdsSkippedInThisPass = skippedNodeIds
        )
        status == EdgeStatus.BROKEN && !toleratesBrokenEdge(node = node, edge = edge)
      }
    }

    // Fixpoint: list order is not necessarily topological.
    do {
      var changed = false
      for (node in continuumWorkflow.nodes) {
        if (node.id in skippedNodeIds) continue
        if (!isUntouched(node = node, nodeOutputMap = nodeOutputMap, nodeErrorsMap = nodeErrorsMap)) continue
        if (cannotRun(node)) {
          skippedNodeIds.add(node.id)
          changed = true
        }
      }
    } while (changed)
    return continuumWorkflow.nodes.filter { it.id in skippedNodeIds }
  }

  /**
   * Returns the status of every edge whose source has finished (edges still pending are left
   * out), in `continuumWorkflow.edges` order. Reads node statuses, so call it after the nodes
   * from [getNodesToSkip] are marked SKIPPED.
   */
  fun getEdgeStatuses(
    continuumWorkflow: ContinuumWorkflowModel,
    nodeOutputMap: Map<String, Map<String, PortData>>,
    nodeErrorsMap: Map<String, Map<String, PortData>>
  ): List<Pair<ContinuumWorkflowModel.Edge, EdgeStatus>> {
    val nodesById = continuumWorkflow.nodes.associateBy { it.id }
    return continuumWorkflow.edges.mapNotNull { edge ->
      edgeStatus(edge = edge, nodesById = nodesById, nodeOutputMap = nodeOutputMap, nodeErrorsMap = nodeErrorsMap)
        ?.let { status -> edge to status }
    }
  }
}
