package com.continuum.core.worker.workflow

import com.continuum.core.commons.activity.IContinuumNodeActivity
import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.constant.TaskQueues.WORKFLOW_TASK_QUEUE
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.workflow.IContinuumWorkflow
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Async
import io.temporal.workflow.Promise
import io.temporal.workflow.Workflow
import java.time.Duration

@WorkflowImpl(taskQueues = [WORKFLOW_TASK_QUEUE])
class ContinuumWorkflow : IContinuumWorkflow {

    private val LOGGER = Workflow.getLogger(ContinuumWorkflow::class.java)

    private val nodeOutputMap = mutableMapOf<String, Map<String, PortData>>()

    private val retryOptions: RetryOptions = RetryOptions {
        setMaximumInterval(Duration.ofSeconds(100))
        setBackoffCoefficient(2.0)
        setMaximumAttempts(500)
    }

    private val baseActivityOptions: ActivityOptions = ActivityOptions {
        setStartToCloseTimeout(Duration.ofSeconds(60))
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
    ) {
        LOGGER.info("Starting ContinuumWorkflowImpl")
        run(continuumWorkflow)
    }

    private fun run(
        continuumWorkflow: ContinuumWorkflowModel
    ) {
        do {
            val nodesToExecute = getNextNodesToExecute(
                continuumWorkflow,
                nodeOutputMap
            )
            val nodeExecutionPromises = nodesToExecute.map { node ->
                val nodeInputs = getNodeInputs(continuumWorkflow, node)
                Pair(node.id, Async.function {
                    continuumNodeActivity.run(node, nodeInputs)
                })
            }
            Promise.allOf(nodeExecutionPromises.map { it.second }).get()
            nodeExecutionPromises.forEach {
                nodeOutputMap[it.first] = it.second.get()
            }
            LOGGER.info("All nodes executed----------------------------------")
        } while (nodesToExecute.isNotEmpty())
    }

    private fun getNodeInputs(
        continuumWorkflow: ContinuumWorkflowModel,
        node: ContinuumWorkflowModel.Node
    ): Map<String, PortData> {
        val nodeParentEdges = continuumWorkflow.getParentEdges(node)
        val nodeInputs = nodeParentEdges.associate { edge ->
            edge.targetHandle to nodeOutputMap[edge.source]!![edge.sourceHandle]!!
        }
        return nodeInputs
    }

    private fun getNextNodesToExecute(
        continuumWorkflow: ContinuumWorkflowModel,
        nodeOutputMap: Map<String, Any>
    ): List<ContinuumWorkflowModel.Node> {
        val nodesToExecute = mutableListOf<ContinuumWorkflowModel.Node>()
        for (node in continuumWorkflow.nodes) {
            val nodeParents = continuumWorkflow.getParentNodes(node)
            // if all the parents has produced the output
            val allParentsProducedOutput = nodeParents.all { parent ->
                nodeOutputMap.containsKey(parent.id)
            }
            if (allParentsProducedOutput && !nodeOutputMap.containsKey(node.id)) {
                nodesToExecute.add(node)
            }
        }
        return nodesToExecute
    }
}