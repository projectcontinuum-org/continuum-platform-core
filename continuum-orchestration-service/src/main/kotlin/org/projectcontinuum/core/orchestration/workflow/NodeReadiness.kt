package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus

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

  /**
   * Extension point: whether a node can still run when [parent] is FAILED or SKIPPED.
   * Always false for now; a later task (branch conditions) may let specific nodes tolerate
   * skipped parents.
   */
  @Suppress("UNUSED_PARAMETER")
  private fun toleratesUnrunnableParent(
    node: ContinuumWorkflowModel.Node,
    parent: ContinuumWorkflowModel.Node
  ) = false

  /** Returns the nodes whose parents have all produced the connected outputs and that haven't started. */
  fun getReadyNodes(
    continuumWorkflow: ContinuumWorkflowModel,
    nodeOutputMap: Map<String, Map<String, PortData>>,
    nodeErrorsMap: Map<String, Map<String, PortData>>
  ): List<ContinuumWorkflowModel.Node> = continuumWorkflow.nodes.filter { node ->
    if (!isUntouched(node, nodeOutputMap, nodeErrorsMap)) return@filter false
    if (hasUnconnectedInput(continuumWorkflow, node)) return@filter false
    val parentEdges = continuumWorkflow.getParentEdges(node)
    continuumWorkflow.getParentNodes(node).all { parent ->
      val outputs = nodeOutputMap[parent.id]
      outputs != null && parentEdges.filter { it.source == parent.id }.all { outputs.containsKey(it.sourceHandle) }
    }
  }

  /**
   * Returns the nodes that can never run: untouched nodes with an unconnected declared input,
   * or with a parent that is FAILED or SKIPPED (transitively, including nodes found skippable
   * in this same pass). Result is in `continuumWorkflow.nodes` list order.
   */
  fun getNodesToSkip(
    continuumWorkflow: ContinuumWorkflowModel,
    nodeOutputMap: Map<String, Map<String, PortData>>,
    nodeErrorsMap: Map<String, Map<String, PortData>>
  ): List<ContinuumWorkflowModel.Node> {
    val skipped = mutableSetOf<String>()
    fun isUnrunnable(parent: ContinuumWorkflowModel.Node) =
      nodeErrorsMap.containsKey(parent.id) ||
          parent.data.status == NodeStatus.FAILED ||
          parent.data.status == NodeStatus.SKIPPED ||
          parent.id in skipped

    // Fixpoint: list order is not necessarily topological.
    do {
      var changed = false
      for (node in continuumWorkflow.nodes) {
        if (node.id in skipped || !isUntouched(node, nodeOutputMap, nodeErrorsMap)) continue
        val cannotRun = hasUnconnectedInput(continuumWorkflow, node) ||
            continuumWorkflow.getParentNodes(node).any { isUnrunnable(it) && !toleratesUnrunnableParent(node, it) }
        if (cannotRun) {
          skipped.add(node.id)
          changed = true
        }
      }
    } while (changed)
    return continuumWorkflow.nodes.filter { it.id in skipped }
  }
}
