package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome

/** When a condition was checked, for messages. */
sealed interface ConditionCheckPoint {
  /** The text that follows "checked" or "returned ... ": `in iteration 3`, `before the first iteration`. */
  val phrase: String

  data object BeforeFirstIteration : ConditionCheckPoint {
    override val phrase: String = "before the first iteration"
  }

  data class AfterIteration(val iteration: Int) : ConditionCheckPoint {
    override val phrase: String get() = "in iteration $iteration"
  }
}

/** What the loop does after one condition check. */
sealed interface LoopDecision {
  /** The condition holds: run the next iteration (or apply the limit). */
  data object Continue : LoopDecision

  /** The loop ends normally. */
  data object StopSuccess : LoopDecision

  /** The loop ends with a warning; the nodes after it still run. */
  data class StopWithWarning(val message: String) : LoopDecision

  /** The loop and the run fail. */
  data class Fail(val message: String) : LoopDecision
}

/**
 * What a condition that returned something other than true or false does (the loop's `onNonBooleanResult`). One
 * strategy per property value, so a new behaviour is a new object, not a new branch.
 */
sealed interface NonBooleanResultStrategy {
  val propertyValue: String

  fun decide(loopId: String, outcome: LoopConditionOutcome.NonBoolean, checkPoint: ConditionCheckPoint): LoopDecision

  /** Fail the loop and the run, naming the type, a preview of the value and the iteration. */
  data object Fail : NonBooleanResultStrategy {
    override val propertyValue: String = "fail"

    override fun decide(loopId: String, outcome: LoopConditionOutcome.NonBoolean, checkPoint: ConditionCheckPoint): LoopDecision =
      LoopDecision.Fail(
        message = "Loop '$loopId': the condition returned ${withArticle(typeName = outcome.typeName)} (${outcome.preview}) " +
          "${checkPoint.phrase}, not true or false."
      )
  }

  /** Stop as if the condition were false, but end with a warning so the unusual result stays visible. */
  data object TreatAsFalse : NonBooleanResultStrategy {
    override val propertyValue: String = "treat-as-false"

    override fun decide(loopId: String, outcome: LoopConditionOutcome.NonBoolean, checkPoint: ConditionCheckPoint): LoopDecision =
      LoopDecision.StopWithWarning(message = "Condition returned ${outcome.typeName} ${checkPoint.phrase}; treated as false.")
  }

  companion object {
    private val all: List<NonBooleanResultStrategy> = listOf(Fail, TreatAsFalse)

    fun forPropertyValue(value: String): NonBooleanResultStrategy? = all.firstOrNull { strategy -> strategy.propertyValue == value }

    private fun withArticle(typeName: String): String = if (typeName.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "an $typeName" else "a $typeName"
  }
}

/** Maps one condition outcome to the loop's decision. Pure. */
object ConditionOutcomePolicy {

  fun decide(
    loopId: String,
    outcome: LoopConditionOutcome,
    onNonBooleanResult: NonBooleanResultStrategy,
    checkPoint: ConditionCheckPoint
  ): LoopDecision = when (outcome) {
    LoopConditionOutcome.True -> LoopDecision.Continue
    LoopConditionOutcome.False -> LoopDecision.StopSuccess
    is LoopConditionOutcome.NonBoolean -> onNonBooleanResult.decide(loopId = loopId, outcome = outcome, checkPoint = checkPoint)
    is LoopConditionOutcome.Failed -> LoopDecision.Fail(message = failureMessage(loopId = loopId, failed = outcome, checkPoint = checkPoint))
  }

  private fun failureMessage(loopId: String, failed: LoopConditionOutcome.Failed, checkPoint: ConditionCheckPoint): String {
    val variableName = failed.variableName
    if (failed.code == LoopConditionIssueCode.MISSING_VARIABLE && variableName != null) {
      val columnText = failed.column?.let { column -> " (column $column)" }.orEmpty()
      return "Loop '$loopId': flow variable '$variableName' is not set when the condition is checked ${checkPoint.phrase}$columnText."
    }
    return "Loop '$loopId': the condition check ${checkPoint.phrase} failed with ${failed.code.name}: the condition ${failed.message}."
  }
}
