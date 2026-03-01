package com.continuum.core.worker.workflow

import com.continuum.core.commons.activity.IContinuumNodeActivity
import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.constant.TaskQueues.WORKFLOW_TASK_QUEUE
import com.continuum.core.commons.model.*
import com.continuum.core.commons.prototol.progress.ContinuumNodeActivitySignal
import com.continuum.core.commons.prototol.progress.NodeProgress
import com.continuum.core.commons.workflow.IContinuumWorkflow
import com.continuum.core.worker.utils.StatusHelper
import com.fasterxml.jackson.databind.ObjectMapper
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.failure.ApplicationFailure
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Async
import io.temporal.workflow.Promise
import io.temporal.workflow.Workflow
import io.temporal.workflow.unsafe.WorkflowUnsafe
import java.time.Duration
import java.time.Instant

/**
 * Temporal workflow implementation for executing Continuum workflows.
 *
 * This class orchestrates the execution of Continuum workflow graphs, managing the
 * parallel execution of nodes, data flow between nodes, and status reporting.
 * It implements the [IContinuumWorkflow] interface and is registered as a Temporal
 * workflow on the configured task queue.
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
 * @see ContinuumWorkflowModel
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

  /**
   * Retry options for activity execution.
   * Configures exponential backoff with a maximum of 500 attempts.
   */
  private val retryOptions: RetryOptions = RetryOptions {
    setMaximumInterval(Duration.ofSeconds(100))
    setBackoffCoefficient(2.0)
    setMaximumAttempts(500)
  }

  /** Jackson ObjectMapper for JSON serialization */
  private val objectMapper = ObjectMapper()

  /**
   * Base activity options for node execution.
   * Activities can run for up to 60 days with the configured retry policy.
   */
  private val baseActivityOptions: ActivityOptions = ActivityOptions {
    setStartToCloseTimeout(Duration.ofDays(60))
    setRetryOptions(retryOptions)
    setTaskQueue(TaskQueues.ACTIVITY_TASK_QUEUE)
  }

  /** Activity stub for executing node activities */
  private val continuumNodeActivity = Workflow.newActivityStub(
    IContinuumNodeActivity::class.java,
    ActivityOptions {
      mergeActivityOptions(baseActivityOptions)
//      setHeartbeatTimeout(Duration.ofMinutes(5))
    }
  )

  /**
   * Starts the workflow execution.
   *
   * This is the main entry point for workflow execution. It performs the following:
   * 1. Initializes the workflow state and publishes "STARTED" event
   * 2. Updates Temporal search attributes to track execution status
   * 3. Executes the workflow DAG via the [run] method
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

    try {
      // Initialize workflow state
      currentRunningWorkflow = continuumWorkflow
      sendUpdateEvent("STARTED")

      // Update Temporal search attribute to indicate workflow has started
      Workflow.upsertTypedSearchAttributes(
        IContinuumWorkflow.WORKFLOW_STATUS
          .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_STARTED.value)
      )

      // Execute the workflow DAG
      run(continuumWorkflow)

      // Update search attribute to indicate successful completion
      Workflow.upsertTypedSearchAttributes(
        IContinuumWorkflow.WORKFLOW_STATUS
          .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_COMPLETED.value)
      )
      sendUpdateEvent("FINISHED")
    } catch (e: Exception) {
      // Handle workflow-level errors
      LOGGER.error("Error in executing workflow", e)
      Workflow.upsertTypedSearchAttributes(
        IContinuumWorkflow.WORKFLOW_STATUS
          .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_FAILED.value)
      )
      sendUpdateEvent("FAILED")
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
   * Executes the workflow DAG by processing nodes in dependency order.
   *
   * This method implements the core workflow execution loop:
   * 1. Find all nodes whose dependencies are satisfied (parents completed)
   * 2. Start those nodes as async activities
   * 3. Wait for any node to complete
   * 4. Update status based on success/failure
   * 5. Repeat until all nodes are processed
   *
   * Nodes are executed in parallel when possible - any nodes whose parent
   * dependencies are satisfied will start concurrently.
   *
   * @param continuumWorkflow The workflow model to execute
   */
  private fun run(
    continuumWorkflow: ContinuumWorkflowModel
  ) {
    // Track running node promises for parallel execution
    val nodeExecutionPromises =
      mutableListOf<Pair<ContinuumWorkflowModel.Node, Promise<IContinuumNodeActivity.NodeActivityOutput>>>()

    do {
      // Find nodes ready to execute (all parents have completed)
      val nodesToExecute = getNextNodesToExecute(
        continuumWorkflow,
        nodeToOutputsMap
      )
      LOGGER.info("Nodes to execute: ${nodesToExecute.map { it.id }}")

      // Start each ready node as an async activity
      val morePromises = nodesToExecute.map { node ->
        // Gather inputs from parent nodes' outputs
        val nodeInputs = getNodeInputs(continuumWorkflow, node)
        // Mark node as running and animate incoming edges
        setNodeAnimationAndStatus(node, ContinuumWorkflowModel.NodeStatus.BUSY)
        // Start the activity asynchronously
        Pair(node, Async.function { continuumNodeActivity.run(node, nodeInputs) })
      }
      nodeExecutionPromises.addAll(morePromises)

      // Publish current state to Kafka
      sendUpdateEvent()

      if (nodeExecutionPromises.isNotEmpty()) {
        // Wait for any node to complete (race condition)
        val nodeOutput = Promise.anyOf(nodeExecutionPromises.map { it.second }).get()
        val completedNode = continuumWorkflow.nodes.first { it.id == nodeOutput.nodeId }

        // Check if node succeeded or failed based on error output port
        if (!nodeOutput.outputs.containsKey(IContinuumNodeActivity.NodeOutputSystemPort.ERROR.key)) {
          // Success: update status and store outputs
          setNodeAnimationAndStatus(completedNode, ContinuumWorkflowModel.NodeStatus.SUCCESS)
          nodeToOutputsMap[nodeOutput.nodeId] = nodeOutput.outputs
        } else {
          // Failure: update status and store error info
          setNodeAnimationAndStatus(completedNode, ContinuumWorkflowModel.NodeStatus.FAILED)
          nodeErrorsMap[nodeOutput.nodeId] = nodeOutput.outputs
        }

        // Remove completed node from pending promises
        nodeExecutionPromises.removeAll { it.first.id == nodeOutput.nodeId }
      }
      LOGGER.info("NodeExecutionPromises size: ${nodeExecutionPromises.size}")
    } while (getNextNodesToExecute(
        continuumWorkflow,
        nodeToOutputsMap
      ).isNotEmpty() || nodeExecutionPromises.isNotEmpty()
    )
    LOGGER.info("All nodes executed----------------------------------")
  }

  /**
   * Gathers input data for a node from its parent nodes' outputs.
   *
   * This method finds all edges connecting to the target node and maps
   * the source node's output port data to the target node's input ports.
   *
   * @param continuumWorkflow The workflow model containing edge definitions
   * @param node The node for which to gather inputs
   * @return Map of input port IDs to their [PortData]
   */
  private fun getNodeInputs(
    continuumWorkflow: ContinuumWorkflowModel,
    node: ContinuumWorkflowModel.Node
  ): Map<String, PortData> {
    // Get all edges where this node is the target
    val nodeParentEdges = continuumWorkflow.getParentEdges(node)
    // Map each edge's source output to the target input port
    val nodeInputs = nodeParentEdges.associate { edge ->
      edge.targetHandle to nodeToOutputsMap[edge.source]!![edge.sourceHandle]!!
    }
    return nodeInputs
  }

  /**
   * Determines which nodes are ready to execute.
   *
   * A node is ready to execute when:
   * 1. All parent nodes have completed successfully
   * 2. All required input ports have data available
   * 3. The node hasn't been executed yet
   * 4. The node doesn't have a status (not currently running)
   *
   * @param continuumWorkflow The workflow model containing node and edge definitions
   * @param nodeOutputMap Map of completed node IDs to their outputs
   * @return List of nodes ready for execution
   */
  private fun getNextNodesToExecute(
    continuumWorkflow: ContinuumWorkflowModel,
    nodeOutputMap: Map<String, Map<String, PortData>>
  ): List<ContinuumWorkflowModel.Node> {
    val nodesToExecute = mutableListOf<ContinuumWorkflowModel.Node>()

    for (node in continuumWorkflow.nodes) {
      // Get parent nodes and connecting edges
      val nodeParents = continuumWorkflow.getParentNodes(node)
      val nodeParentEdges = continuumWorkflow.getParentEdges(node)

      // Check if all parent nodes have produced the required outputs
      val allParentsProducedOutput = nodeParents.all { parent ->
        val connectingEdgesToParent = nodeParentEdges.filter { it.source == parent.id }
        // Parent must have outputs AND all connected output ports must have data
        nodeOutputMap.containsKey(parent.id) &&
            connectingEdgesToParent.all { edge ->
              nodeOutputMap[parent.id]?.containsKey(edge.sourceHandle) ?: false
            }
      }

      LOGGER.debug(
        "Node: {} allParentsProducedOutput: {} executed: {} status: {}",
        node.id,
        allParentsProducedOutput,
        nodeOutputMap.containsKey(node.id),
        node.data.status
      )

      // Node is ready if: parents done, not yet executed, and not currently running
      if (allParentsProducedOutput &&
        !nodeOutputMap.containsKey(node.id) &&
        node.data.status == null
      ) {
        nodesToExecute.add(node)
      }
    }
    return nodesToExecute
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
  private fun sendUpdateEvent(
    status: String = "RUNNING"
  ) {
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
          progressPercentage = 0,
          status = status,
          nodeToOutputsMap = nodeToOutputsMapWithErr,
          createdAtTimestampUtc = Workflow.getInfo().runStartedTimestampMillis,
          updatesAtTimestampUtc = Instant.now().toEpochMilli(),
          workflow = currentRunningWorkflow!!
        )
      )

      // Publish to Kafka via StatusHelper
      StatusHelper.publishWorkflowSnapshot(
        Workflow.getInfo().workflowId,
        eventMetadata
      )
    }
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
  private fun setNodeAnimationAndStatus(
    node: ContinuumWorkflowModel.Node,
    nodeStatus: ContinuumWorkflowModel.NodeStatus
  ) {
    // Update node status
    node.data.status = nodeStatus
    // Animate/de-animate incoming edges based on node state
    currentRunningWorkflow?.edges
      ?.filter { it.target == node.id }
      ?.forEach {
        it.animated = nodeStatus == ContinuumWorkflowModel.NodeStatus.BUSY
      }
  }

  /**
   * Returns a snapshot of the current workflow state.
   *
   * This query method can be called by external clients to get the current
   * state of node outputs without affecting workflow execution.
   *
   * @return [WorkflowSnapshot] containing current node outputs
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
   * This signal is called by [ContinuumNodeActivity] to report execution progress
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
    sendUpdateEvent()
  }
}