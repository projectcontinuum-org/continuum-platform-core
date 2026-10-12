package org.projectcontinuum.core.knime.scheduler.model

import java.time.Instant
import java.util.UUID

data class KnimeWorkflowExecutionResponse(
  val executionId: UUID,
  val workflowId: UUID,
  val fileName: String,
  val sizeBytes: Long,
  val contentType: String?,
  val status: String,
  val createdAt: Instant
)
