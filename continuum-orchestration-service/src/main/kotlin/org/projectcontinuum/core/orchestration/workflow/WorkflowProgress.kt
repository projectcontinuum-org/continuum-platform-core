package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.model.PortData

/**
 * The run's progress percentage, for [WorkflowStatusReporter]. Pure.
 *
 * Progress is the share of nodes that are done: succeeded, failed or skipped. Skipped nodes count because they will
 * never run, so a run with skipped nodes still ends at 100%. Loop nodes are not counted: a loop is a box
 * around its members and has no activity of its own. A workflow without countable nodes is at 100%.
 */
internal object WorkflowProgress {

  fun percentage(
    nodes: List<ContinuumWorkflowModel.Node>,
    nodeToOutputsMap: Map<String, Map<String, PortData>>,
    nodeErrorsMap: Map<String, Map<String, PortData>>
  ): Int {
    val countedNodes = nodes.filterNot { node -> LoopNode.isLoop(node = node) }
    if (countedNodes.isEmpty()) return 100
    val doneNodeCount = countedNodes.count { node ->
      nodeToOutputsMap.containsKey(node.id) ||
        nodeErrorsMap.containsKey(node.id) ||
        node.data.status == ContinuumWorkflowModel.NodeStatus.SKIPPED
    }
    return (doneNodeCount * 100) / countedNodes.size
  }
}

/**
 * Keeps the published progress from going backwards: a loop resets its members between iterations, so
 * the raw share of done nodes can drop; the published value is the highest one so far. Workflow-scoped state, updated
 * at the same points in a live run and in a replay, so it is deterministic.
 */
internal class MonotonicProgress {

  private var highestPercentage: Int = 0

  /** Records [rawPercentage] and returns the value to publish: never less than any value returned before. */
  fun next(rawPercentage: Int): Int {
    highestPercentage = maxOf(highestPercentage, rawPercentage)
    return highestPercentage
  }
}
