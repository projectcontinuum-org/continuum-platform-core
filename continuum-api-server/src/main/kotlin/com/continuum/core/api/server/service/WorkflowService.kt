package com.continuum.core.api.server.service

import com.continuum.core.api.server.model.WorkflowStatus
import com.continuum.core.api.server.utils.TreeHelper
import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.ExecutionStatus
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.model.WorkflowSnapshot
import com.continuum.core.commons.workflow.IContinuumWorkflow
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.api.enums.v1.EventType
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowQueryRejectedException
import io.temporal.common.SearchAttributes
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@EnableScheduling
class WorkflowService(
    val workflowClient: WorkflowClient
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WorkflowService::class.java)
        private val objectMapper: ObjectMapper = ObjectMapper()
    }

    val treeRoots = mutableListOf<TreeHelper.TreeItem<TreeHelper.Execution>>()

    fun startWorkflow(
        continuumWorkflowModel: ContinuumWorkflowModel
    ): String {
        val continuumWorkflow = workflowClient.newWorkflowStub(
            IContinuumWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE)
                .setTypedSearchAttributes(SearchAttributes.newBuilder()
                    .set(IContinuumWorkflow.WORKFLOW_FILE_PATH, continuumWorkflowModel.name)
                    .set(IContinuumWorkflow.WORKFLOW_STATUS, ExecutionStatus.SCHEDULED.value)
                    .build())
                .build()
        )
        val workflowExecution: WorkflowExecution = WorkflowClient.start(
            continuumWorkflow::start,
            continuumWorkflowModel
        )
        return workflowExecution.workflowId
    }

    fun getActiveWorkflows(): List<WorkflowStatus> {
        return workflowClient.workflowServiceStubs.blockingStub()
            .listWorkflowExecutions(
                ListWorkflowExecutionsRequest.newBuilder()
                    .setNamespace(workflowClient.options.namespace)
                    .setQuery("WorkflowType='${IContinuumWorkflow::class.java.simpleName}' && ExecutionStatus='WORKFLOW_EXECUTION_STATUS_RUNNING'")
                    .build()
            ).executionsList.map { WorkflowStatus(
                it.execution.workflowId,
                it.type.name,
                it.status,
                it.searchAttributes.indexedFieldsMap
                    .filterKeys { !it.equals("BuildIds") }
                    .mapValues { it.value.data.toStringUtf8() }
            ) }
    }

    fun listAllWorkflow(
        nextToken: ByteString? = null
    ): List<WorkflowStatus> {
        val requestBuilder = ListWorkflowExecutionsRequest.newBuilder()
            .setNamespace(workflowClient.options.namespace)
            .setQuery("WorkflowType='${IContinuumWorkflow::class.java.simpleName}'")
        nextToken?.let { requestBuilder.setNextPageToken(it) }
        val listResponse = workflowClient.workflowServiceStubs.blockingStub()
            .listWorkflowExecutions(requestBuilder.build())
        val workflowsList = listResponse.executionsList.map {
            WorkflowStatus(
                it.execution.workflowId,
                it.type.name,
                it.status,
                it.searchAttributes.indexedFieldsMap
                    .filterKeys { key -> !key.equals("BuildIds") }
                    .mapValues { value -> value.value.data.toStringUtf8() }
            )
        }
        val workflowMutableList = workflowsList.toMutableList()
        if(listResponse.nextPageToken.size() > 0) {
            val otherList = listAllWorkflow(listResponse.nextPageToken)
            workflowMutableList.addAll(otherList)
        }
        return workflowMutableList
    }

    fun getWorkflowStatusById(
        workflowId: String
    ): WorkflowStatus {
        val result = workflowClient.workflowServiceStubs.blockingStub()
            .listWorkflowExecutions(
                ListWorkflowExecutionsRequest.newBuilder()
                    .setNamespace(workflowClient.options.namespace)
                    .setQuery("WorkflowType='${IContinuumWorkflow::class.java.simpleName}' && WorkflowId='$workflowId'")
                    .build()
            ).executionsList.map { WorkflowStatus(
                it.execution.workflowId,
                it.type.name,
                it.status,
                it.searchAttributes.indexedFieldsMap
                    .filterKeys { !it.equals("BuildIds") }
                    .mapValues { it.value.data.toStringUtf8() }
            ) }
        return if (result.isNotEmpty()) {
            result[0]
        } else {
            throw RuntimeException("Workflow with ID $workflowId not found")
        }
    }

    fun getWorkflowTree(
        baseDir: String
    ): List<TreeHelper.TreeItem<TreeHelper.Execution>> {
        val subTree = TreeHelper.getSubTree(treeRoots, baseDir.split("/").toMutableList()).toMutableList()
        TreeHelper.sortTree(subTree) { first, second ->
            first.itemInfo!!.createdAtTimestampUtc.compareTo(second.itemInfo!!.createdAtTimestampUtc) * -1
        }
        return subTree
    }

    @Scheduled(fixedDelay = 5000)
    fun refreshWorkflowTree() {
        val baseDir = ""
        LOGGER.info("Refreshing Workflow tree...")
        listAllWorkflow().filter { it.metadata[IContinuumWorkflow.WORKFLOW_FILE_PATH.name]?.startsWith("\"$baseDir") ?: false }.forEach {
            val categoryPath = it.metadata[IContinuumWorkflow.WORKFLOW_FILE_PATH.name]
                // remove the first and last '"'
                ?.replace("\"", "")
                ?.replace(baseDir, "")
                ?.split("/")?.toMutableList() ?: mutableListOf()
            val history = workflowClient.fetchHistory(it.workflowId)
            val startEvent = history.events
                .firstOrNull { evt -> evt.eventType == EventType.EVENT_TYPE_WORKFLOW_TASK_SCHEDULED }
            val lastEvent = history.lastEvent
            val workflowInputString = history.events[0].workflowExecutionStartedEventAttributes.input.payloadsList[0].data.toStringUtf8()
            val workflowInput = objectMapper.readValue(workflowInputString, ContinuumWorkflowModel::class.java)
            var workflowOutput: Map<String, Map<String, PortData>>? = null
            if(lastEvent.eventType == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_COMPLETED) {
                val workflowOutputString = lastEvent.workflowExecutionCompletedEventAttributes.result.payloadsList[0].data.toStringUtf8()
                workflowOutput = objectMapper.readValue(workflowOutputString, object : TypeReference<Map<String, Map<String, PortData>>>() {})
            } else {
                try {
                    workflowOutput = workflowClient.newWorkflowStub(
                        IContinuumWorkflow::class.java,
                        it.workflowId
                    ).getWorkflowSnapshot().nodeToOutputsMap
                } catch (e: WorkflowQueryRejectedException) {
                    workflowOutput = mapOf()
                    LOGGER.error("Error querying workflow!")
                }
            }
            // Get the start and end time of the workflow id
            TreeHelper.addItemToParent(treeRoots,
                TreeHelper.Execution(
                    id = it.workflowId,
                    status = ExecutionStatus.fromHistoryEvents(history.events),
                    workflow_snapshot = workflowInput,
                    nodeToOutputsMap = workflowOutput,
                    workflowId = it.workflowId,
                    createdAtTimestampUtc = Instant.ofEpochSecond(startEvent?.eventTime?.seconds ?: 0,
                        (startEvent?.eventTime?.nanos ?: 0).toLong()
                    ).toEpochMilli(),
                    updatesAtTimestampUtc = Instant.ofEpochSecond(lastEvent?.eventTime?.seconds ?: 0,
                        (lastEvent?.eventTime?.nanos ?: 0).toLong()
                    ).toEpochMilli()
                ),
                categoryPath
            )
        }
    }
}