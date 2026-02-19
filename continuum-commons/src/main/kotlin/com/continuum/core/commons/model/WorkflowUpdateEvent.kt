package com.continuum.core.commons.model

data class WorkflowUpdateEvent(
  val jobId: String,
  val data: WorkflowUpdate
)