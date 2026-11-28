package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.protocol.progress.NodeProgress
import org.projectcontinuum.core.commons.utils.FlowVariables

/**
 * Writes a loop's run display into the model the status events carry: `data.status` (BUSY while iterating, then
 * SUCCESS, WARNING, FAILED or SKIPPED), `data.nodeProgress` (`Iteration <k> of <max>`, then the final message), the
 * display-only `$variable` (what the entry members of the last iteration received; omitted when empty) and, for a
 * failed loop, `$error` in the errors map, which also fails the run.
 */
internal class LoopStatusPublisher(private val context: LoopRunContext) {

  fun iterationStarted(loopId: String, iteration: Int, maxIterations: Int) {
    val loop = context.node(nodeId = loopId)
    context.setNodeStatus(loop, NodeStatus.BUSY)
    loop.data.nodeProgress = NodeProgress(
      progressPercentage = iteration * 100 / maxIterations,
      message = "Iteration $iteration of $maxIterations"
    )
  }

  /** The loop ended normally or with a warning; [carriedIntoLastIteration] becomes its `$variable`. */
  fun finished(loopId: String, status: NodeStatus, message: String, carriedIntoLastIteration: Map<String, Any>) {
    val loop = context.node(nodeId = loopId)
    context.setNodeStatus(loop, status)
    loop.data.nodeProgress = NodeProgress(progressPercentage = 100, message = message)
    if (carriedIntoLastIteration.isNotEmpty()) {
      context.nodeToOutputsMap[loopId] = mapOf(NodeOutputSystemPort.VARIABLE.key to FlowVariables.toPortData(carriedIntoLastIteration))
    }
  }

  /** The loop failed: FAILED, the message as progress text and as `$error`. */
  fun failed(loopId: String, message: String, iteration: Int, maxIterations: Int) {
    val loop = context.node(nodeId = loopId)
    context.setNodeStatus(loop, NodeStatus.FAILED)
    loop.data.nodeProgress = NodeProgress(progressPercentage = iteration * 100 / maxIterations.coerceAtLeast(1), message = message)
    context.nodeErrorsMap[loopId] = mapOf(
      NodeOutputSystemPort.ERROR.key to PortData(
        tableSpec = emptyList(),
        data = message,
        contentType = "text/plain",
        status = PortDataStatus.FAILED
      )
    )
  }

  /** The loop was skipped after it started, with [message] saying why; a skipped loop is not a failure. */
  fun skipped(loopId: String, message: String) {
    val loop = context.node(nodeId = loopId)
    context.setNodeStatus(loop, NodeStatus.SKIPPED)
    loop.data.nodeProgress = NodeProgress(progressPercentage = 100, message = message)
  }

  /** Marks every node inside [loopId] that has not run SKIPPED (for zero iterations, a skipped or failed start). */
  fun skipUntouchedDescendants(loopId: String) {
    context.topology.descendants(loopId = loopId).forEach { nodeId ->
      val node = context.node(nodeId = nodeId)
      val untouched = node.data.status == null &&
        !context.nodeToOutputsMap.containsKey(nodeId) &&
        !context.nodeErrorsMap.containsKey(nodeId)
      if (untouched) context.setNodeStatus(node, NodeStatus.SKIPPED)
    }
  }
}
