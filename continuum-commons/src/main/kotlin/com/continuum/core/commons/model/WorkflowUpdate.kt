package com.continuum.core.commons.model

data class WorkflowUpdate(
    val executionUUID: String,
    val progressPercentage: Int,
    val status: String,
    val nodeToOutputsMap: Map<String, Any>,
    val createdAtTimestampUtc: Long,
    val updatesAtTimestampUtc: Long,
    val workflow: ContinuumWorkflowModel
)