package com.continuum.core.commons.model

import io.temporal.api.enums.v1.EventType.*
import io.temporal.api.history.v1.HistoryEvent

enum class ExecutionStatus(val value: Long) {
    SCHEDULED(0),
    RUNNING(10),
    COMPLETED(20),
    FAILED(30),
    TIMED_OUT(50);

    companion object {
        fun fromHistoryEvents(eventHistory: List<HistoryEvent>): ExecutionStatus {
            val lastWorkflowEvent = eventHistory
                .filter { it.eventTypeValue in EVENT_TYPE_WORKFLOW_EXECUTION_STARTED_VALUE..EVENT_TYPE_WORKFLOW_EXECUTION_TIMED_OUT_VALUE }
                .maxBy { it.eventTime.seconds + (it.eventTime.nanos * 1e-9)}
            return when (lastWorkflowEvent.eventType) {
                EVENT_TYPE_WORKFLOW_EXECUTION_STARTED -> RUNNING
                EVENT_TYPE_WORKFLOW_EXECUTION_COMPLETED -> COMPLETED
                EVENT_TYPE_WORKFLOW_EXECUTION_FAILED -> FAILED
                EVENT_TYPE_WORKFLOW_EXECUTION_TIMED_OUT -> TIMED_OUT
                else -> SCHEDULED
            }
        }
    }
}