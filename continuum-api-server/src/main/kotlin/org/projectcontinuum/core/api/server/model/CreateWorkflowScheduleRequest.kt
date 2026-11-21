package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

@Schema(name = "CreateWorkflowScheduleRequest", description = "A cron schedule that starts the given workflow repeatedly.")
data class CreateWorkflowScheduleRequest(
  @field:Schema(
    description = "Schedule name, unique per owner by convention (not enforced). The workbench uses `<workflow path>/<schedule>.schedule`.",
    example = "examples/text-cleanup.cwf/nightly.schedule"
  )
  val name: String,
  @field:Schema(
    description = "Cron expression in Temporal's syntax: 5 fields (minute hour day-of-month month day-of-week) or a macro such as `@daily`.",
    example = "0 2 * * *"
  )
  val cronExpression: String,
  @field:Schema(description = "IANA time zone the cron expression is evaluated in. Default UTC.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "Europe/Berlin")
  val timeZone: String? = null,
  @field:Schema(description = "The workflow to run on each fire. Validated like a start; stored as sent.")
  val continuumWorkflowModel: ContinuumWorkflowModel
)
