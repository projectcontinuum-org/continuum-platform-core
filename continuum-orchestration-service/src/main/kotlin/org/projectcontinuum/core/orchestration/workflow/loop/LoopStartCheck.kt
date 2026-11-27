package org.projectcontinuum.core.orchestration.workflow.loop

import io.temporal.workflow.Workflow
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionValidator

/**
 * The defensive check when a loop starts: the api-server validates every workflow before it runs, but a run started
 * directly through Temporal skips that. An invalid condition or setting fails the loop with the reason, never with an
 * exception (an exception would fail the workflow task, which Temporal retries forever).
 *
 * The settings are checked against the Loop descriptor's values; the condition with the injected [validator] (the
 * same cel-java validator the api-server uses). Its result is recorded so a cel-java upgrade cannot change the
 * start decision when Temporal replays this workflow.
 */
class LoopStartCheck(
  private val validator: LoopConditionValidator,
  private val recorder: LoopStartCheckRecorder = LoopStartCheckRecorder.DIRECT
) {

  sealed interface Result {
    data class Accepted(val settings: LoopSettings) : Result
    data class Rejected(val message: String) : Result
  }

  fun check(loopId: String, properties: Map<String, Any?>): Result {
    val recorded = recorder.record {
      RecordedLoopStartCheck.from(
        result = validate(loopId = loopId, properties = properties)
      )
    }
    return recorded.toResult()
  }

  private fun validate(loopId: String, properties: Map<String, Any?>): Result {
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

/** Records the loop-start verdict in Temporal history, independently of the current CEL validator on replay. */
fun interface LoopStartCheckRecorder {
  fun record(validate: () -> RecordedLoopStartCheck): RecordedLoopStartCheck

  companion object {
    val TEMPORAL = LoopStartCheckRecorder { validate ->
      Workflow.sideEffect(RecordedLoopStartCheck::class.java) { validate() }
    }
    val DIRECT = LoopStartCheckRecorder { validate -> validate() }
  }
}

/**
 * A [LoopStartCheck.Result] in the plain, no-argument shape Temporal stores in its history.
 * The CEL AST and compiled program are deliberately not persisted; the verdict and settings are.
 */
class RecordedLoopStartCheck(
  var accepted: Boolean = false,
  var message: String? = null,
  var condition: String? = null,
  var maxIterations: Int = 0,
  var onLimit: String? = null,
  var onNonBooleanResult: String? = null,
  var check: String? = null
) {

  fun toResult(): LoopStartCheck.Result {
    if (!accepted) return LoopStartCheck.Result.Rejected(message = message.orEmpty())
    return LoopStartCheck.Result.Accepted(
      settings = LoopSettings(
        condition = condition.orEmpty(),
        maxIterations = maxIterations,
        onLimit = LimitBehaviour.valueOf(onLimit.orEmpty()),
        onNonBooleanResult = NonBooleanResultStrategy.forPropertyValue(value = onNonBooleanResult.orEmpty())
          ?: error("Recorded loop-start verdict has an unknown non-boolean strategy"),
        check = ConditionCheckTiming.valueOf(check.orEmpty())
      )
    )
  }

  companion object {
    fun from(result: LoopStartCheck.Result): RecordedLoopStartCheck = when (result) {
      is LoopStartCheck.Result.Rejected -> RecordedLoopStartCheck(
        accepted = false,
        message = result.message
      )
      is LoopStartCheck.Result.Accepted -> RecordedLoopStartCheck(
        accepted = true,
        condition = result.settings.condition,
        maxIterations = result.settings.maxIterations,
        onLimit = result.settings.onLimit.name,
        onNonBooleanResult = result.settings.onNonBooleanResult.propertyValue,
        check = result.settings.check.name
      )
    }
  }
}
