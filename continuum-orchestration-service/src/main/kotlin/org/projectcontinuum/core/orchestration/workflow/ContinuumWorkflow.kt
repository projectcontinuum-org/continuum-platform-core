package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.constant.TaskQueues.WORKFLOW_TASK_QUEUE
import org.projectcontinuum.core.commons.protocol.progress.ContinuumNodeActivitySignal
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import io.temporal.failure.ActivityFailure
import io.temporal.failure.ApplicationFailure
import io.temporal.failure.CanceledFailure
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.ExecutionStatus
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.WorkflowSnapshot

/**
 * Temporal workflow implementation for executing Continuum workflows.
 *
 * This class orchestrates the execution of Continuum workflow graphs, managing the
 * parallel execution of nodes, data flow between nodes, and status reporting.
 * It implements the [IContinuumWorkflow] interface and is registered as a Temporal
 * workflow on the configured task queue.
 *
 * Responsibilities are split across three collaborators so this class stays a thin
 * orchestrator:
 * - [WorkflowActivityInitializer] resolves task queues and builds per-node activity stubs
 * - [WorkflowDagExecutor] runs the DAG scheduling loop
 * - [WorkflowStatusReporter] publishes Kafka status updates and tracks node/edge animation
 *
 * ## Workflow Execution Model
 * The workflow uses a directed acyclic graph (DAG) execution model where:
 * - Nodes are executed when all their parent nodes have completed
 * - Multiple independent nodes can execute in parallel
 * - Data flows through edges connecting node output ports to input ports
 *
 * ## Key Features
 * - **Parallel Execution**: Nodes without dependencies execute concurrently
 * - **Progress Tracking**: Real-time progress updates via Temporal signals
 * - **State Persistence**: Workflow state survives worker failures via Temporal
 * - **Event Publishing**: Status changes are published to Kafka for UI updates
 * - **Error Handling**: Failed nodes are tracked and reported in workflow results
 *
 * ## Temporal Search Attributes
 * - `Continuum:ExecutionStatus` - Tracks workflow execution status
 * - `Continuum:WorkflowFileName` - Identifies the workflow file
 *
 * @author Continuum Team
 * @since 1.0.0
 * @see IContinuumWorkflow
 * @see org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
 * @see IContinuumNodeActivity
 */
@WorkflowImpl(taskQueues = [WORKFLOW_TASK_QUEUE])
class ContinuumWorkflow : IContinuumWorkflow {

  /** Workflow-scoped logger (compatible with Temporal's replay mechanism) */
  private val LOGGER = Workflow.getLogger(ContinuumWorkflow::class.java)

  /** Map of completed node IDs to their output port data */
  private val nodeToOutputsMap = mutableMapOf<String, Map<String, PortData>>()

  /** Map of failed node IDs to their error output data */
  private val nodeErrorsMap = mutableMapOf<String, Map<String, PortData>>()

  /** The currently executing workflow model (for status updates) */
  private var currentRunningWorkflow: ContinuumWorkflowModel? = null

  private var nodeIdToActivityMap: Map<String, IContinuumNodeActivity> = emptyMap()

  private val activityInitializer = WorkflowActivityInitializer()

  private val statusReporter = WorkflowStatusReporter(
    currentRunningWorkflow = { currentRunningWorkflow },
    nodeToOutputsMap = nodeToOutputsMap,
    nodeErrorsMap = nodeErrorsMap
  )

  private val dagExecutor = WorkflowDagExecutor(statusReporter)

  /**
   * Starts the workflow execution.
   *
   * This is the main entry point for workflow execution. It performs the following:
   * 1. Initializes the workflow state and publishes "STARTED" event
   * 2. Updates Temporal search attributes to track execution status
   * 3. Executes the workflow DAG via [WorkflowDagExecutor.run]
   * 4. Publishes completion/failure events
   * 5. Returns all node outputs or throws an exception if any node failed
   *
   * @param continuumWorkflow The workflow model containing nodes and edges to execute
   * @return Map of node IDs to their output port data
   * @throws ApplicationFailure if any node failed during execution (non-retriable)
   */
  override fun start(
    continuumWorkflow: ContinuumWorkflowModel
  ): Map<String, Map<String, PortData>> {
    LOGGER.info("Starting ContinuumWorkflowImpl")

    nodeIdToActivityMap = activityInitializer.initializeNodeActivityStubs(continuumWorkflow)

    try {
      // Initialize workflow state
      currentRunningWorkflow = continuumWorkflow
      statusReporter.publishUpdate("STARTED")

      // Update Temporal search attribute to indicate workflow has started
      Workflow.upsertTypedSearchAttributes(
        IContinuumWorkflow.WORKFLOW_STATUS
          .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_STARTED.value)
      )

      // Execute the workflow DAG
      dagExecutor.run(continuumWorkflow, nodeIdToActivityMap, nodeToOutputsMap, nodeErrorsMap)

      // Update search attribute to indicate successful completion
      Workflow.upsertTypedSearchAttributes(
        IContinuumWorkflow.WORKFLOW_STATUS
          .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_COMPLETED.value)
      )
      statusReporter.publishUpdate("FINISHED")
    } catch (e: CanceledFailure) {
      handleCancellation(e)
      throw e
    } catch (e: ActivityFailure) {
      if (e.cause is CanceledFailure) {
        // The activity was cancelled as part of workflow cancellation/termination
        // propagating down to it, not an actual node failure.
        handleCancellation(e)
        throw e
      }
      // Handle activity-level failures at the workflow level (e.g., initialization failures)
      // Note: Node-level activity failures are now caught and converted to node errors,
      // so this catch block should rarely be reached except for workflow-level activities
      handleUnexpectedFailure(e)
      throw e
    } catch (e: Exception) {
      // Handle unexpected workflow-level errors
      handleUnexpectedFailure(e)
    }

    // If any nodes failed, throw an ApplicationFailure with details
    if (nodeErrorsMap.isNotEmpty()) {
      throw ApplicationFailure
        .newNonRetryableFailure(
          "Workflow execution failed",
          "WorkflowExecutionFailed",
          mapOf(
            "nodeErrors" to nodeErrorsMap,
            "nodeToOutputsMap" to nodeToOutputsMap
          )
        )
    }
    return nodeToOutputsMap
  }

  /**
   * Handles workflow cancellation or termination reaching [start] — either as a direct
   * [CanceledFailure] or one wrapping an [ActivityFailure]. Marks in-progress nodes
   * CANCELLED, pins the search attribute and all subsequent status updates to CANCELLED,
   * and publishes the final event.
   */
  private fun handleCancellation(e: Throwable) {
    LOGGER.warn("Workflow was cancelled or terminated", e)
    statusReporter.markBusyNodesAs(ContinuumWorkflowModel.NodeStatus.CANCELLED)
    Workflow.upsertTypedSearchAttributes(
      IContinuumWorkflow.WORKFLOW_STATUS
        .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_CANCELED.value)
    )
    statusReporter.pinTerminalStatus("CANCELLED")
    statusReporter.publishUpdate("CANCELLED")
  }

  /**
   * Handles unexpected workflow-level failures reaching [start] (as opposed to node-level
   * failures, which are already converted to node error outputs by [WorkflowDagExecutor]).
   * Marks in-progress nodes FAILED and publishes the failure event.
   */
  private fun handleUnexpectedFailure(e: Throwable) {
    LOGGER.error("Error in executing workflow", e)
    statusReporter.markBusyNodesAs(ContinuumWorkflowModel.NodeStatus.FAILED)
    Workflow.upsertTypedSearchAttributes(
      IContinuumWorkflow.WORKFLOW_STATUS
        .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_FAILED.value)
    )
    statusReporter.publishUpdate("FAILED")
  }

  /**
   * Returns a snapshot of the current workflow state.
   *
   * This query method can be called by external clients to get the current
   * state of node outputs without affecting workflow execution.
   *
   * @return [org.projectcontinuum.core.commons.model.WorkflowSnapshot] containing current node outputs
   */
  override fun getWorkflowSnapshot(): WorkflowSnapshot {
    return WorkflowSnapshot(
      //            workflowSnapshot = currentRunningWorkflow!!,
      nodeToOutputsMap = nodeToOutputsMap
    )
  }

  /**
   * Signal handler for receiving node progress updates from activities.
   *
   * This signal is called by activity workers to report execution progress
   * for long-running nodes. The progress is stored in the node's data and a
   * status update is published to Kafka.
   *
   * **Note**: This signal can be called frequently, so processing is kept minimal.
   *
   * @param continuumNodeActivitySignal Signal containing node ID and progress info
   */
  override fun updateNodeProgressSignal(
    continuumNodeActivitySignal: ContinuumNodeActivitySignal
  ) {
    val nodeProgress = continuumNodeActivitySignal.nodeProgress
    // Log the progress update
    LOGGER.info("Received node progress signal: ${nodeProgress.progressPercentage}% - ${nodeProgress.message ?: ""}")

    // Find the node and update its progress
    val nodeToUpdate = currentRunningWorkflow?.nodes
      ?.find { it.id == continuumNodeActivitySignal.nodeId }!!
    nodeToUpdate.data.nodeProgress = nodeProgress

    LOGGER.info("Node id: ${nodeToUpdate.data.id} Node title: ${nodeToUpdate.data.title} Node current progress: ${nodeToUpdate.data.nodeProgress?.progressPercentage ?: "null"}%")

    // Publish updated state to Kafka
    statusReporter.publishUpdate()
  }

  /**
   * Signal handler invoked by the API server just before it issues a Temporal
   * `cancel()` request. Pins all subsequent status updates to the real final
   * "CANCELLED" status, closing the window where an in-flight DAG execution iteration
   * or node progress signal could otherwise report a stale "RUNNING" status
   * after cancellation has already been requested.
   *
   * @param reason Optional human-readable reason for the cancellation
   */
  override fun notifyCancelling(reason: String?) {
    LOGGER.info("Received cancellation notice. Reason: ${reason ?: "none"}")
    statusReporter.pinTerminalStatus("CANCELLED")
    statusReporter.publishUpdate("CANCELLED")
  }

  /**
   * Signal handler invoked by the API server just before it issues a Temporal
   * `terminate()` request. Pins all subsequent status updates to the real final
   * "TERMINATED" status and updates the search attribute directly, since this is
   * the only workflow code that will ever run on the termination path — Temporal's
   * `terminate()` gives the workflow no chance to run any code afterward.
   *
   * @param reason Optional human-readable reason for the termination
   */
  override fun notifyTerminating(reason: String?) {
    LOGGER.info("Received termination notice. Reason: ${reason ?: "none"}")
    statusReporter.pinTerminalStatus("TERMINATED")
    Workflow.upsertTypedSearchAttributes(
      IContinuumWorkflow.WORKFLOW_STATUS
        .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_TERMINATED.value)
    )
    statusReporter.publishUpdate("TERMINATED")
  }
}
