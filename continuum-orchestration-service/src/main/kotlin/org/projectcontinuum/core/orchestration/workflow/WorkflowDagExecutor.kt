package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import io.temporal.failure.ActivityFailure
import io.temporal.failure.CanceledFailure
import io.temporal.workflow.Async
import io.temporal.workflow.Promise
import io.temporal.workflow.Workflow

/**
 * Executes a Continuum workflow DAG by dispatching node activities in dependency order,
 * on behalf of a [ContinuumWorkflow].
 *
 * @param statusReporter Used to keep node/edge animation state and Kafka status updates
 *   in sync as nodes start and complete.
 */
class WorkflowDagExecutor(
  private val statusReporter: WorkflowStatusReporter
) {

  private val LOGGER = Workflow.getLogger(WorkflowDagExecutor::class.java)

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
   * @param nodeIdToActivityMap Per-node activity stubs, keyed by node instance ID
   * @param nodeToOutputsMap Mutable map this method populates with completed node outputs
   * @param nodeErrorsMap Mutable map this method populates with failed node error outputs
   */
  fun run(
    continuumWorkflow: ContinuumWorkflowModel,
    nodeIdToActivityMap: Map<String, IContinuumNodeActivity>,
    nodeToOutputsMap: MutableMap<String, Map<String, PortData>>,
    nodeErrorsMap: MutableMap<String, Map<String, PortData>>
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
        val nodeInputs = getNodeInputs(continuumWorkflow, node, nodeToOutputsMap)
        // Mark node as running and animate incoming edges
        statusReporter.setNodeAnimationAndStatus(node, ContinuumWorkflowModel.NodeStatus.BUSY)
        // Start the activity asynchronously, catching ActivityFailure to treat it as a node error
        Pair(node, Async.function {
          try {
            nodeIdToActivityMap[node.id]!!.run(node, nodeInputs)
          } catch (e: ActivityFailure) {
            if (e.cause is CanceledFailure) {
              // The activity was cancelled as part of workflow cancellation/termination
              // propagating down to it, not an actual node failure. Rethrow so it surfaces
              // to start()'s CanceledFailure/ActivityFailure handling instead of being
              // recorded as a node-level error.
              throw e
            }
            // Convert activity-level failure (e.g., retries exhausted, timeout) to node error
            // This allows the workflow to continue executing other independent nodes
            LOGGER.error("Activity failure for node ${node.id}: ${e.cause?.message ?: e.message}", e)
            IContinuumNodeActivity.NodeActivityOutput(
              nodeId = node.id,
              outputs = mapOf(
                IContinuumNodeActivity.NodeOutputSystemPort.ERROR.key to PortData(
                  tableSpec = emptyList(),
                  data = "Activity failure: ${e.cause?.message ?: e.message}",
                  contentType = "text/plain",
                  status = PortDataStatus.FAILED
                )
              )
            )
          }
        })
      }
      nodeExecutionPromises.addAll(morePromises)

      // Publish current state to Kafka
      statusReporter.publishUpdate()

      if (nodeExecutionPromises.isNotEmpty()) {
        // Wait for any node to complete (race condition)
        val nodeOutput = Promise.anyOf(nodeExecutionPromises.map { it.second }).get()
        val completedNode = continuumWorkflow.nodes.first { it.id == nodeOutput.nodeId }

        // Check if node succeeded or failed based on error output port
        if (!nodeOutput.outputs.containsKey(IContinuumNodeActivity.NodeOutputSystemPort.ERROR.key)) {
          // Success: update status and store outputs
          statusReporter.setNodeAnimationAndStatus(completedNode, ContinuumWorkflowModel.NodeStatus.SUCCESS)
          nodeToOutputsMap[nodeOutput.nodeId] = nodeOutput.outputs
        } else {
          // Failure: update status and store error info
          statusReporter.setNodeAnimationAndStatus(completedNode, ContinuumWorkflowModel.NodeStatus.FAILED)
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
   * @param nodeToOutputsMap Map of completed node IDs to their outputs
   * @return Map of input port IDs to their [PortData]
   */
  private fun getNodeInputs(
    continuumWorkflow: ContinuumWorkflowModel,
    node: ContinuumWorkflowModel.Node,
    nodeToOutputsMap: Map<String, Map<String, PortData>>
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

      // Check if all input ports are connected to a parent node's output port
      val allInputPortsConnected = node.data.inputs?.all { inputPort ->
          nodeParentEdges.any { edge -> edge.targetHandle == inputPort.key }
      } ?: true

      if(!allInputPortsConnected) {
        node.data.status = ContinuumWorkflowModel.NodeStatus.SKIPPED
      }

      LOGGER.debug(
        "Node: {} allParentsProducedOutput: {} allInputPortsConnected: {} executed: {} status: {}",
        node.id,
        allParentsProducedOutput,
        allInputPortsConnected,
        nodeOutputMap.containsKey(node.id),
        node.data.status
      )

      // Node is ready if: parents done, not yet executed, and not currently running
      if (allParentsProducedOutput &&
        allInputPortsConnected &&
        !nodeOutputMap.containsKey(node.id) &&
        node.data.status == null
      ) {
        nodesToExecute.add(node)
      }
    }
    return nodesToExecute
  }
}
