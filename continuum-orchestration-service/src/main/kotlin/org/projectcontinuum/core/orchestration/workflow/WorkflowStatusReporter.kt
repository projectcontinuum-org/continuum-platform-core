package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.WorkflowUpdate
import org.projectcontinuum.core.commons.model.WorkflowUpdateEvent
import org.projectcontinuum.core.orchestration.utils.StatusHelper
import io.temporal.workflow.Workflow
import io.temporal.workflow.unsafe.WorkflowUnsafe
import java.time.Instant

/**
 * Publishes workflow status updates to Kafka and keeps node/edge animation state in sync
 * with node execution status, on behalf of a [ContinuumWorkflow].
 *
 * @param currentRunningWorkflow Provider for the workflow model currently being executed.
 *   Passed as a lambda (rather than a direct reference) because [ContinuumWorkflow] sets
 *   this field after constructing its reporter, and remains the single source of truth for it.
 * @param nodeToOutputsMap Live view of completed node IDs to their output port data
 * @param nodeErrorsMap Live view of failed node IDs to their error output data
 */
class WorkflowStatusReporter(
  private val currentRunningWorkflow: () -> ContinuumWorkflowModel?,
  private val nodeToOutputsMap: Map<String, Map<String, PortData>>,
  private val nodeErrorsMap: Map<String, Map<String, PortData>>
) {

  private val LOGGER = Workflow.getLogger(WorkflowStatusReporter::class.java)

  /**
   * Once set, overrides the status argument to every subsequent [publishUpdate] call.
   * Set when a stop is requested, and superseded by the real final status once the
   * stop actually completes (e.g. CANCELLED).
   */
  private var terminalStatus: String? = null

  /** Pins all subsequent [publishUpdate] calls to the given status, overriding their argument. */
  fun pinTerminalStatus(status: String) {
    terminalStatus = status
  }

  /**
   * Publishes a workflow status update event to Kafka.
   *
   * This method creates a [WorkflowUpdateEvent] containing the current workflow state
   * and publishes it via [StatusHelper] for real-time UI updates.
   *
   * **Note**: This method only publishes events during live execution, not during
   * Temporal workflow replay to avoid duplicate events.
   *
   * @param status The workflow status string (e.g., "STARTED", "RUNNING", "FINISHED", "FAILED")
   */
  fun publishUpdate(
    status: String = "RUNNING"
  ) {
    // Once a stop has been signalled, terminalStatus pins every subsequent event to the
    // latest known stopping/final status, regardless of what the caller passed in.
    val effectiveStatus = terminalStatus ?: status
    // Check if the Workflow is being replayed - skip publishing during replay
    if (!WorkflowUnsafe.isReplaying()) {
      // Combine successful outputs and error outputs for the event
      val nodeToOutputsMapWithErr = mutableMapOf<String, Map<String, PortData>>()
      nodeToOutputsMapWithErr.putAll(nodeToOutputsMap)
      nodeToOutputsMapWithErr.putAll(nodeErrorsMap)

      // Create the workflow update event with current state
      val eventMetadata = WorkflowUpdateEvent(
        jobId = Workflow.getInfo().workflowId,
        data = WorkflowUpdate(
          executionUUID = Workflow.getInfo().workflowId,
          progressPercentage = calculateProgressPercentage(),
          status = effectiveStatus,
          nodeToOutputsMap = nodeToOutputsMapWithErr,
          createdAtTimestampUtc = Workflow.getInfo().runStartedTimestampMillis,
          updatesAtTimestampUtc = Instant.now().toEpochMilli(),
          workflow = currentRunningWorkflow()!!
        )
      )

      // Publish to Kafka via StatusHelper
      StatusHelper.Companion.publishWorkflowSnapshot(
        Workflow.getInfo().workflowId,
        eventMetadata
      )
    }
  }

  /**
   * Calculates the overall workflow progress percentage.
   *
   * This method computes progress based on the number of completed nodes
   * (both successful and failed) relative to the total number of nodes in the workflow.
   *
   * @return Progress percentage as an integer between 0 and 100
   */
  fun calculateProgressPercentage(): Int {
    val totalNodes = currentRunningWorkflow()?.nodes?.size ?: 0
    if (totalNodes == 0) return 100
    val completedNodes = nodeToOutputsMap.size + nodeErrorsMap.size
    return (completedNodes * 100) / totalNodes
  }

  /**
   * Updates a node's status and animates/de-animates its incoming edges.
   *
   * When a node starts execution (BUSY), its incoming edges are animated to show
   * data flow. When execution completes (SUCCESS/FAILED), animation is removed.
   *
   * @param node The node to update
   * @param nodeStatus The new status for the node
   */
  fun setNodeAnimationAndStatus(
    node: ContinuumWorkflowModel.Node,
    nodeStatus: ContinuumWorkflowModel.NodeStatus
  ) {
    // Update node status
    node.data.status = nodeStatus
    // Animate/de-animate incoming edges based on node state
    currentRunningWorkflow()?.edges
      ?.filter { it.target == node.id }
      ?.forEach {
        it.animated = nodeStatus == ContinuumWorkflowModel.NodeStatus.BUSY
      }
  }

  /**
   * Marks all nodes currently in BUSY status with the given status.
   *
   * This is used during workflow failure, cancellation, or termination to
   * update any in-progress nodes so the UI reflects their final state.
   *
   * @param status The status to assign to busy nodes (e.g., FAILED, CANCELLED)
   */
  fun markBusyNodesAs(status: ContinuumWorkflowModel.NodeStatus) {
    currentRunningWorkflow()?.nodes
      ?.filter { it.data.status == ContinuumWorkflowModel.NodeStatus.BUSY }
      ?.forEach { setNodeAnimationAndStatus(it, status) }
  }
}
