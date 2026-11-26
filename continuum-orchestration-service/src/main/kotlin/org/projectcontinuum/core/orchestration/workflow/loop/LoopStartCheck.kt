package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionValidator

/**
 * The defensive check when a loop starts: the api-server validates every workflow before it runs, but a run started
 * directly through Temporal skips that. An invalid condition or setting fails the loop with the reason, never with an
 * exception (an exception would fail the workflow task, which Temporal retries forever).
 *
 * The settings are checked against the Loop descriptor's values; the condition with the injected [validator] (the
 * same cel-java validator the api-server uses).
 */
class LoopStartCheck(private val validator: LoopConditionValidator) {

  sealed interface Result {
    data class Accepted(val settings: LoopSettings) : Result
    data class Rejected(val message: String) : Result
  }

  fun check(loopId: String, properties: Map<String, Any?>): Result {
    val problems = mutableListOf<String>()
    val condition = properties[LoopNodeDescriptor.CONDITION_PROPERTY] as? String
    if (condition == null) problems += "condition is missing or not a string"
    val maxIterations = wholeNumberOf(value = properties[LoopNodeDescriptor.MAX_ITERATIONS_PROPERTY])
    val iterationRange = LoopNodeDescriptor.MIN_MAX_ITERATIONS..LoopNodeDescriptor.MAX_MAX_ITERATIONS
    if (maxIterations == null || maxIterations !in iterationRange) {
      problems += "maxIterations must be a whole number from ${iterationRange.first} to ${iterationRange.last}, got " +
        "${properties[LoopNodeDescriptor.MAX_ITERATIONS_PROPERTY]}"
    }
    val onLimit = enumValueOf(property = LoopNodeDescriptor.ON_LIMIT_PROPERTY, properties = properties, problems = problems) { value ->
      LimitBehaviour.entries.firstOrNull { behaviour -> behaviour.propertyValue == value }
    }
    val onNonBooleanResult = enumValueOf(property = LoopNodeDescriptor.ON_NON_BOOLEAN_RESULT_PROPERTY, properties = properties, problems = problems) { value ->
      NonBooleanResultStrategy.forPropertyValue(value = value)
    }
    val check = enumValueOf(property = LoopNodeDescriptor.CHECK_PROPERTY, properties = properties, problems = problems) { value ->
      ConditionCheckTiming.entries.firstOrNull { timing -> timing.propertyValue == value }
    }
    if (condition != null) {
      val conditionResult = validator.check(text = condition)
      if (conditionResult is LoopConditionCheckResult.Invalid) problems += "the condition ${conditionResult.message}"
    }
    if (problems.isNotEmpty()) {
      return Result.Rejected(message = "Loop '$loopId' cannot start: ${problems.joinToString(separator = "; ")}.")
    }
    return Result.Accepted(
      settings = LoopSettings(
        condition = condition!!,
        maxIterations = maxIterations!!,
        onLimit = onLimit!!,
        onNonBooleanResult = onNonBooleanResult!!,
        check = check!!
      )
    )
  }

  private fun <T> enumValueOf(
    property: String,
    properties: Map<String, Any?>,
    problems: MutableList<String>,
    lookup: (String) -> T?
  ): T? {
    val rawValue = properties[property]
    val resolved = (rawValue as? String)?.let(lookup)
    if (resolved == null) problems += "$property '$rawValue' is not one of its allowed values"
    return resolved
  }

  /** A JSON number without a fraction (Temporal's JSON may hand it over as Int, Long or Double). */
  private fun wholeNumberOf(value: Any?): Int? = when (value) {
    is Int -> value
    is Long -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else null
    is Number -> value.toDouble().takeIf { number -> number == Math.floor(number) && !number.isInfinite() }?.toInt()
    else -> null
  }
}
