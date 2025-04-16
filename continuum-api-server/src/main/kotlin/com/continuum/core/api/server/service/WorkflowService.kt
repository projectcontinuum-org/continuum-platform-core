package com.continuum.core.api.server.service

import com.continuum.core.api.server.model.WorkflowStatus
import com.continuum.core.api.server.utils.TreeHelper
import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.ExecutionStatus
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.workflow.IContinuumWorkflow
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ByteString
import com.google.protobuf.MapEntry
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.api.enums.v1.EventType
import io.temporal.api.workflowservice.v1.CountWorkflowExecutionsRequest
import io.temporal.api.workflowservice.v1.CountWorkflowExecutionsResponse
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowException
import io.temporal.client.WorkflowOptions
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

    fun startWorkflow(
        continuumWorkflowModel: ContinuumWorkflowModel
    ): String {
        val continuumWorkflow = workflowClient.newWorkflowStub(
            IContinuumWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE)
                .setTypedSearchAttributes(SearchAttributes.newBuilder()
                    .set(IContinuumWorkflow.WORKFLOW_FILE_PATH, continuumWorkflowModel.name)
                    .set(IContinuumWorkflow.WORKFLOW_STATUS, ExecutionStatus.UNKNOWN.value)
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
            ).executionsList.map {execution -> WorkflowStatus(
                execution.execution.workflowId,
                execution.type.name,
                execution.status,
                execution.searchAttributes.indexedFieldsMap
                    .filterKeys { !it.equals("BuildIds") }
                    .mapValues { it.value.data.toStringUtf8() }
            ) }
    }

    fun listAllWorkflow(
        query: String = "",
        nextToken: ByteString? = null
    ): List<WorkflowStatus> {
        val requestBuilder = ListWorkflowExecutionsRequest.newBuilder()
            .setNamespace(workflowClient.options.namespace)
            .setQuery(query)
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
            val otherList = listAllWorkflow(query, listResponse.nextPageToken)
            workflowMutableList.addAll(otherList)
        }
        return workflowMutableList
    }

    fun countWorkflow(
        query: String
    ): CountWorkflowExecutionsResponse {
        val requestBuilder = CountWorkflowExecutionsRequest.newBuilder()
            .setNamespace(workflowClient.options.namespace)
            .setQuery(query)
        val  countWorkflowExecutionsResponse = workflowClient.workflowServiceStubs.blockingStub()
            .countWorkflowExecutions(requestBuilder.build())
        return countWorkflowExecutionsResponse
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
            ).executionsList.map { execution -> WorkflowStatus(
                execution.execution.workflowId,
                execution.type.name,
                execution.status,
                execution.searchAttributes.indexedFieldsMap
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
        baseDir: String,
        query: String = ""
    ): List<TreeHelper.TreeItem<TreeHelper.Execution>> {
        val treeRoots = loadWorkflowTree(baseDir, query)
        TreeHelper.sortTree(treeRoots) { first, second ->
            if(first.itemInfo == null || second.itemInfo == null) {
                // compare names if itemInfo is null
                first.name.compareTo(second.name)
            } else {
                first.itemInfo.createdAtTimestampUtc.compareTo(second.itemInfo.createdAtTimestampUtc) * -1
            }
        }
        return treeRoots
    }

    private fun loadWorkflowTree(
        baseDir: String,
        query: String
    ): MutableList<TreeHelper.TreeItem<TreeHelper.Execution>> {
        val treeRoots = mutableListOf<TreeHelper.TreeItem<TreeHelper.Execution>>()
        LOGGER.info("Refreshing Workflow tree...")
        listAllWorkflow(query).forEach {
            val categoryPath = it.metadata[IContinuumWorkflow.WORKFLOW_FILE_PATH.name]
                ?.replace("\"", "")
                ?.replace(baseDir, "")
                ?.split("/")
                ?.drop(1)
                ?.toMutableList() ?: mutableListOf()
            val history = workflowClient.fetchHistory(it.workflowId)
            val startEvent = history.events
                .firstOrNull { evt -> evt.eventType == EventType.EVENT_TYPE_WORKFLOW_TASK_SCHEDULED }
            val lastEvent = history.lastEvent
            val workflowInputString = history.events[0].workflowExecutionStartedEventAttributes.input.payloadsList[0].data.toStringUtf8()
            val workflowInput = objectMapper.readValue(workflowInputString, ContinuumWorkflowModel::class.java)
            var workflowOutput: Map<String, Map<String, PortData>>?
            if(lastEvent.eventType == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_COMPLETED) {
                val workflowOutputString = lastEvent.workflowExecutionCompletedEventAttributes.result.payloadsList[0].data.toStringUtf8()
                workflowOutput = objectMapper.readValue(workflowOutputString, object : TypeReference<Map<String, Map<String, PortData>>>() {})
            } else {
                try {
                    // Project the workflow events into output
                    LOGGER.debug("Querying workflowId: ${it.workflowId} for output...")
                    workflowOutput = history.events.filter { evt -> evt.eventType == EventType.EVENT_TYPE_ACTIVITY_TASK_COMPLETED }
                        .associate { evt ->
                            val outputStr = evt.activityTaskCompletedEventAttributes.result.payloadsList[0].data.toStringUtf8()
                            val nodeOutput = objectMapper.readValue(outputStr, object : TypeReference<Map<String, Any>>() {})
                            val nodeId = nodeOutput["nodeId"] as String
                            val output = objectMapper.readValue(
                                objectMapper.writeValueAsString(nodeOutput["outputs"]),
                                object : TypeReference<Map<String, PortData>>() {}
                            )
                            LOGGER.debug("NodeId: {}, output: {}", nodeId, output)
                            nodeId to output
                        }.filter { entry -> entry.value.isNotEmpty() }
                    LOGGER.debug(
                        "Querying workflowId: {} for output... {} outputs found!",
                        it.workflowId,
                        workflowOutput
                    )

//                    workflowOutput = workflowClient.newWorkflowStub(
//                        IContinuumWorkflow::class.java,
//                        it.workflowId
//                    ).getWorkflowSnapshot().nodeToOutputsMap
                } catch (e: WorkflowException) {
                    workflowOutput = mapOf()
                    LOGGER.error("Error querying workflowId: ${it.workflowId}!")
                }
            }
            // Get the start and end time of the workflow id
            TreeHelper.addItemToParent(
                treeRoots = treeRoots,
                execution = TreeHelper.Execution(
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
                categoryPath = categoryPath,
                maxChildren = 100
            )
        }
        return treeRoots
    }
}