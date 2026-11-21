package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/** Result of `POST /api/v1/admin/schedules/validate`: stored schedules checked against the current node registry. */
@Schema(name = "ScheduleValidationReport", description = "Every stored schedule checked against the current node registry.")
data class ScheduleValidationReport(
  /** Number of stored schedules checked. */
  @field:Schema(description = "Number of stored schedules checked.", example = "57")
  val checked: Int,
  /** Schedules with at least one node whose properties violate its registered schema. Every run of these fails. */
  @field:Schema(description = "Schedules with at least one node whose properties violate its registered schema. Every run of these fails.")
  val invalid: List<ScheduleValidationResult>,
  /** Schedules whose only problem is a node type that is not registered (its worker may just not be up). */
  @field:Schema(description = "Schedules whose only problem is an unregistered node type (its worker may just not be up).")
  val unregisteredOnly: List<ScheduleValidationResult>,
  /** Schedules that could not be checked (e.g. a registry read failed); retry the call. */
  @field:Schema(description = "Ids of schedules that could not be checked (e.g. a registry read failed); retry the call.")
  val failedToCheck: List<UUID>
) {
  @get:Schema(description = "True when no schedule is invalid, unregistered-only or unchecked.", example = "false")
  val valid: Boolean get() = invalid.isEmpty() && unregisteredOnly.isEmpty() && failedToCheck.isEmpty()
}

@Schema(name = "ScheduleValidationResult", description = "One stored schedule with problems.")
data class ScheduleValidationResult(
  @field:Schema(description = "Schedule id.", example = "1d0f3c52-7a3b-4c8e-9d4f-2b6a8e1f0c3d")
  val scheduleId: UUID,
  @field:Schema(description = "Schedule name.", example = "examples/text-cleanup.cwf/nightly.schedule")
  val name: String,
  @field:Schema(description = "Owner of the schedule.", example = "alice")
  val ownedBy: String,
  @field:Schema(description = "One entry per failing node.")
  val problems: List<Problem>
) {
  @Schema(name = "ScheduleValidationProblem", description = "One node of a stored schedule that will fail.")
  data class Problem(
    @field:Schema(description = "Node id in the schedule's workflow.", example = "normalize")
    val nodeId: String,
    @field:Schema(description = "Registered node type.", example = "org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel")
    val nodeModel: String,
    @field:Schema(
      description = "`INVALID_PROPERTIES`: properties violate the registered schema. `UNREGISTERED_NODE`: no worker registered this node type.",
      allowableValues = ["INVALID_PROPERTIES", "UNREGISTERED_NODE"],
      example = "INVALID_PROPERTIES"
    )
    val kind: String,
    @field:Schema(description = "What is wrong.", example = "$.inputCol: is missing but it is required")
    val detail: String
  )
}
