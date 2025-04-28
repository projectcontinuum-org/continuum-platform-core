package com.continuum.core.worker

enum class Channels(
    val channelName: String,
) {
    CONTINUUM_WORKFLOW_STATE_CHANGE_EVENT("continuum-core-event-WorkflowExecutionSnapshot-output"),
}