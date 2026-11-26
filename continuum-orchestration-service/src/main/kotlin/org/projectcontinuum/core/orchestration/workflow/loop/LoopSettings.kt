package org.projectcontinuum.core.orchestration.workflow.loop

/** When the condition is checked: before each iteration (the loop may run zero times) or after each one. */
enum class ConditionCheckTiming(val propertyValue: String) {
  BEFORE(propertyValue = "before"),
  AFTER(propertyValue = "after")
}

/** What happens when the condition still holds after `maxIterations`. */
enum class LimitBehaviour(val propertyValue: String) {
  FAIL(propertyValue = "fail"),
  STOP_WITH_WARNING(propertyValue = "stop-with-warning")
}

/** A loop's properties, read and checked at loop start by [LoopStartCheck]. */
data class LoopSettings(
  val condition: String,
  val maxIterations: Int,
  val onLimit: LimitBehaviour,
  val onNonBooleanResult: NonBooleanResultStrategy,
  val check: ConditionCheckTiming
)
