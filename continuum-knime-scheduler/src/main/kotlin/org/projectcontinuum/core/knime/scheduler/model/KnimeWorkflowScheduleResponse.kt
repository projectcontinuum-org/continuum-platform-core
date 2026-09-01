package org.projectcontinuum.core.knime.scheduler.model

import java.time.Instant
import java.util.UUID

data class KnimeWorkflowScheduleResponse(
  val scheduleId: UUID,
  val name: String,
  val ownedBy: String,
  val cronExpression: String,
  val timeZone: String?,
  val paused: Boolean,
  val nextRunTimes: List<Instant>,
  val createdAt: Instant,
  val updatedAt: Instant,
  val knimeWorkflowId: UUID,
  val resetWorkflow: Boolean,
  val timeoutSeconds: Long,
  val workflowVariables: List<KnimeWorkflowVariable>,
  val workflowCredentials: List<KnimeWorkflowCredentialRef>
)
