package org.projectcontinuum.core.api.server.model

import java.time.Instant
import java.util.UUID

data class WorkflowScheduleResponse(
  val scheduleId: UUID,
  val name: String,
  val ownedBy: String,
  val cronExpression: String,
  val timeZone: String?,
  val paused: Boolean,
  val nextRunTimes: List<Instant>,
  val createdAt: Instant,
  val updatedAt: Instant
)
