package com.continuum.core.worker.utils

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.eventstore.dbclient.EventDataBuilder
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.EventStoreDBConnectionString
import com.fasterxml.jackson.databind.ObjectMapper

class EventStoreHelper {
    companion object {
        val objectMapper = ObjectMapper()

        var esdbClient: EventStoreDBClient = EventStoreDBClient.create(
            EventStoreDBConnectionString
                .parseOrThrow("esdb://localhost:2113?tls=false")
        )

        fun sendEvent(event: WorkflowRunEvent) {
            esdbClient.appendToStream(
                "run-id:${event.runId}",
                EventDataBuilder
                    .json(
                        event.javaClass.simpleName,
                        objectMapper.writeValueAsString(event.data())
                    )
                    .metadataAsBytes(
                        objectMapper.writeValueAsString(event.metadata()).toByteArray(Charsets.UTF_8)
                    )
                    .build()
            ).get()
        }
    }

    interface WorkflowRunEvent {
        val workflowId: String
        val runId: String
        fun metadata(): Map<String, String>
        fun data(): Map<String, Any>
    }

    data class WorkflowExecutionStartedEvent(
        override val workflowId: String,
        override val runId: String,
        val workflowFileName: String,
        val workflowModel: ContinuumWorkflowModel
    ): WorkflowRunEvent {
        override fun metadata(): Map<String, String> {
            return mapOf(
                "workflowId" to workflowId,
                "runId" to runId,
                "workflowFileName" to workflowFileName
            )
        }

        override fun data(): Map<String, ContinuumWorkflowModel> {
            return mapOf(
                "workflowModel" to workflowModel
            )
        }
    }

    data class WorkflowExecutionCompletedEvent(
        override val workflowId: String,
        override val runId: String,
        val workflowFileName: String,
        val workflowModel: ContinuumWorkflowModel
    ): WorkflowRunEvent {
        override fun metadata(): Map<String, String> {
            return mapOf(
                "workflowId" to workflowId,
                "runId" to runId,
                "workflowFileName" to workflowFileName
            )
        }

        override fun data(): Map<String, ContinuumWorkflowModel> {
            return mapOf(
                "workflowModel" to workflowModel
            )
        }
    }

    data class WorkflowStatusUpdateEvent(
        override val workflowId: String,
        override val runId: String,
        val workflowUpdate: MqttHelper.WorkflowUpdate
    ): WorkflowRunEvent {
        override fun metadata(): Map<String, String> {
            return mapOf(
                "workflowId" to workflowId,
                "runId" to runId,
                "status" to workflowUpdate.status
            )
        }

        override fun data(): Map<String, MqttHelper.WorkflowUpdate> {
            return mapOf(
                "workflowUpdate" to workflowUpdate
            )
        }
    }
}