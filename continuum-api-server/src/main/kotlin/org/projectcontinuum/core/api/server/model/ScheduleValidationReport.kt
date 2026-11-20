package org.projectcontinuum.core.api.server.model

import java.util.UUID

/** Result of `POST /api/v1/admin/schedules/validate`: stored schedules checked against the current node registry. */
data class ScheduleValidationReport(
  /** Number of stored schedules checked. */
  val checked: Int,
  /** Schedules with at least one node whose properties violate its registered schema. Every run of these fails. */
  val invalid: List<ScheduleValidationResult>,
  /** Schedules whose only problem is a node type that is not registered (its worker may just not be up). */
  val unregisteredOnly: List<ScheduleValidationResult>,
  /** Schedules that could not be checked (e.g. a registry read failed); retry the call. */
  val failedToCheck: List<UUID>
) {
  val valid: Boolean get() = invalid.isEmpty() && unregisteredOnly.isEmpty() && failedToCheck.isEmpty()
}

data class ScheduleValidationResult(
  val scheduleId: UUID,
  val name: String,
  val ownedBy: String,
  val problems: List<Problem>
) {
  data class Problem(val nodeId: String, val nodeModel: String, val kind: String, val detail: String)
}
