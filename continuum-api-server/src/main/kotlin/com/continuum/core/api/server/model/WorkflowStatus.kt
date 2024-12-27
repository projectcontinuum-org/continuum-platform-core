package com.continuum.core.api.server.model

import io.temporal.api.enums.v1.WorkflowExecutionStatus

data class WorkflowStatus(
  val workflowId: String,
  val type: String,
  val temporalStatus: WorkflowExecutionStatus,
  val metadata: Map<String, String>
)
