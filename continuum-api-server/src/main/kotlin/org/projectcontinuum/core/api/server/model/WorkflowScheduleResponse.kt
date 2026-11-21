package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import java.time.Instant
import java.util.UUID

@Schema(name = "WorkflowSchedule", description = "A cron schedule with its live state from Temporal.")
data class WorkflowScheduleResponse(
  @field:Schema(description = "Schedule id; also the Temporal schedule id and the workflow id of the runs it starts.", example = "1d0f3c52-7a3b-4c8e-9d4f-2b6a8e1f0c3d")
  val scheduleId: UUID,
  @field:Schema(description = "Schedule name.", example = "examples/text-cleanup.cwf/nightly.schedule")
  val name: String,
  @field:Schema(description = "Owner of the schedule (and of the runs it starts).", example = "alice")
  val ownedBy: String,
  @field:Schema(description = "Cron expression (Temporal syntax).", example = "0 2 * * *")
  val cronExpression: String,
  @field:Schema(description = "IANA time zone of the cron expression; null means UTC.", nullable = true, example = "Europe/Berlin")
  val timeZone: String?,
  @field:Schema(description = "Whether the schedule is paused (live from Temporal).", example = "false")
  val paused: Boolean,
  @field:Schema(description = "Next fire times computed by Temporal (ISO-8601, UTC). Empty while paused.", example = "[\"2026-11-22T01:00:00Z\"]")
  val nextRunTimes: List<Instant>,
  @field:Schema(description = "When the schedule was created (ISO-8601, UTC).", example = "2026-11-21T09:00:00Z")
  val createdAt: Instant,
  @field:Schema(description = "When the schedule row was last changed (ISO-8601, UTC).", example = "2026-11-21T09:00:00Z")
  val updatedAt: Instant,
  @field:Schema(description = "The workflow each fire runs.")
  val continuumWorkflowModel: ContinuumWorkflowModel
)
