package org.projectcontinuum.core.commons.model

data class WorkflowUpdateEvent(
  val jobId: String,
  val data: WorkflowUpdate
)