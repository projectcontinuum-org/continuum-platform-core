package com.continuum.core.worker.workflow

import com.continuum.core.commons.activity.IContinuumNodeActivity
import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.constant.TaskQueues.WORKFLOW_TASK_QUEUE
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.ExecutionStatus
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.model.WorkflowSnapshot
import com.continuum.core.commons.model.WorkflowUpdate
import com.continuum.core.commons.model.WorkflowUpdateEvent
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


@WorkflowImpl(taskQueues = [WORKFLOW_TASK_QUEUE])
class ContinuumWorkflow : IContinuumWorkflow {

    private val LOGGER = Workflow.getLogger(ContinuumWorkflow::class.java)

    private val nodeToOutputsMap = mutableMapOf<String, Map<String, PortData>>()
    private val nodeErrorsMap = mutableMapOf<String, Map<String, PortData>>()
    private var currentRunningWorkflow: ContinuumWorkflowModel? = null

    private val retryOptions: RetryOptions = RetryOptions {
        setMaximumInterval(Duration.ofSeconds(100))
        setBackoffCoefficient(2.0)
        setMaximumAttempts(500)
    }

    private val objectMapper = ObjectMapper()

    private val baseActivityOptions: ActivityOptions = ActivityOptions {
        setStartToCloseTimeout(Duration.ofDays(60))
        setRetryOptions(retryOptions)
        setTaskQueue(TaskQueues.ACTIVITY_TASK_QUEUE)
    }

    private val continuumNodeActivity = Workflow.newActivityStub(
        IContinuumNodeActivity::class.java,
        ActivityOptions {
            mergeActivityOptions(baseActivityOptions)
        }
    )

    override fun start(
        continuumWorkflow: ContinuumWorkflowModel
    ): Map<String, Map<String, PortData>> {
        LOGGER.info("Starting ContinuumWorkflowImpl")

        try {
            currentRunningWorkflow = continuumWorkflow
            sendUpdateEvent("STARTED")
            Workflow.upsertTypedSearchAttributes(
                IContinuumWorkflow.WORKFLOW_STATUS
                    .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_STARTED.value)
            )
            run(continuumWorkflow)
            Workflow.upsertTypedSearchAttributes(
                IContinuumWorkflow.WORKFLOW_STATUS
                    .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_COMPLETED.value)
            )
            sendUpdateEvent("FINISHED")
        } catch (e: Exception) {
            LOGGER.error("Error in executing workflow", e)
            Workflow.upsertTypedSearchAttributes(
                IContinuumWorkflow.WORKFLOW_STATUS
                    .valueSet(ExecutionStatus.WORKFLOW_EXECUTION_FAILED.value)
            )
            sendUpdateEvent("FAILED")
        }
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

    private fun run(
        continuumWorkflow: ContinuumWorkflowModel
    ) {
        val nodeExecutionPromises = mutableListOf<Pair<ContinuumWorkflowModel.Node, Promise<IContinuumNodeActivity.NodeActivityOutput>>>()
        do {
            val nodesToExecute = getNextNodesToExecute(
                continuumWorkflow,
                nodeToOutputsMap
            )
            LOGGER.info("Nodes to execute: ${nodesToExecute.map { it.id }}")
            val morePromises = nodesToExecute.map { node ->
                val nodeInputs = getNodeInputs(continuumWorkflow, node)
                setNodeAnimationAndStatus(node, ContinuumWorkflowModel.NodeStatus.BUSY)
                Pair(node, Async.function {continuumNodeActivity.run(node, nodeInputs)})
            }
            nodeExecutionPromises.addAll(morePromises)
            sendUpdateEvent()
            if (nodeExecutionPromises.isNotEmpty()) {
                val nodeOutput = Promise.anyOf(nodeExecutionPromises.map { it.second }).get()
                val completedNode = continuumWorkflow.nodes.first { it.id == nodeOutput.nodeId }
                if(!nodeOutput.outputs.containsKey(IContinuumNodeActivity.NodeOutputSystemPort.ERROR.key)) {
                    setNodeAnimationAndStatus(completedNode, ContinuumWorkflowModel.NodeStatus.SUCCESS)
                    nodeToOutputsMap[nodeOutput.nodeId] = nodeOutput.outputs
                } else {
                    setNodeAnimationAndStatus(completedNode, ContinuumWorkflowModel.NodeStatus.FAILED)
                    nodeErrorsMap[nodeOutput.nodeId] = nodeOutput.outputs
                }
                // remove the completed promises
                nodeExecutionPromises.removeAll { it.first.id == nodeOutput.nodeId }
            }
            LOGGER.info("NodeExecutionPromises size: ${nodeExecutionPromises.size}")
        } while (getNextNodesToExecute(
                continuumWorkflow,
                nodeToOutputsMap
            ).isNotEmpty() || nodeExecutionPromises.isNotEmpty())
        LOGGER.info("All nodes executed----------------------------------")
    }

    private fun getNodeInputs(
        continuumWorkflow: ContinuumWorkflowModel,
        node: ContinuumWorkflowModel.Node
    ): Map<String, PortData> {
        val nodeParentEdges = continuumWorkflow.getParentEdges(node)
        val nodeInputs = nodeParentEdges.associate { edge ->
            edge.targetHandle to nodeToOutputsMap[edge.source]!![edge.sourceHandle]!!
        }
        return nodeInputs
    }

    private fun getNextNodesToExecute(
        continuumWorkflow: ContinuumWorkflowModel,
        nodeOutputMap: Map<String, Map<String, PortData>>
    ): List<ContinuumWorkflowModel.Node> {
        val nodesToExecute = mutableListOf<ContinuumWorkflowModel.Node>()
        for (node in continuumWorkflow.nodes) {
            val nodeParents = continuumWorkflow.getParentNodes(node)
            val nodeParentEdges = continuumWorkflow.getParentEdges(node)
            // if all the parents has produced the output
            val allParentsProducedOutput = nodeParents.all { parent ->
                val connectingEdgesToParent = nodeParentEdges.filter { it.source == parent.id }
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
            if (allParentsProducedOutput &&
                !nodeOutputMap.containsKey(node.id) &&
                node.data.status == null) {
                nodesToExecute.add(node)
            }
        }
        return nodesToExecute
    }

    private fun sendUpdateEvent(
        status: String = "RUNNING"
    ) {
        // Check if the Workflow is being replayed
        if (!WorkflowUnsafe.isReplaying()) {
            val nodeToOutputsMapWithErr = mutableMapOf<String, Map<String, PortData>>()
            nodeToOutputsMapWithErr.putAll(nodeToOutputsMap)
            nodeToOutputsMapWithErr.putAll(nodeErrorsMap)
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

            StatusHelper.publishWorkflowSnapshot(
                Workflow.getInfo().workflowId,
                eventMetadata
            )
        }
    }

    private fun setNodeAnimationAndStatus(
        node: ContinuumWorkflowModel.Node,
        nodeStatus: ContinuumWorkflowModel.NodeStatus
    ) {
        node.data.status = nodeStatus
        currentRunningWorkflow?.edges
            ?.filter { it.target == node.id }
            ?.forEach {
                it.animated = nodeStatus == ContinuumWorkflowModel.NodeStatus.BUSY
            }
    }

    override fun getWorkflowSnapshot(): WorkflowSnapshot {
        return WorkflowSnapshot(
//            workflowSnapshot = currentRunningWorkflow!!,
            nodeToOutputsMap = nodeToOutputsMap
        )
    }
}