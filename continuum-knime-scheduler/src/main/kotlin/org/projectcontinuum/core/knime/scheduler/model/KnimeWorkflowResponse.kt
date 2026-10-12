package org.projectcontinuum.core.knime.scheduler.model

import java.time.Instant
import java.util.UUID

data class KnimeWorkflowResponse(
  val workflowId: UUID,
  val fileName: String,
  val sizeBytes: Long,
  val contentType: String?,
  val createdAt: Instant,
  val updatedAt: Instant
)
