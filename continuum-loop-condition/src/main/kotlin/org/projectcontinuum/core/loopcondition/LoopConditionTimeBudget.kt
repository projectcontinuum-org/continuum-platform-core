package org.projectcontinuum.core.loopcondition

import dev.cel.common.ast.CelExpr
import dev.cel.runtime.CelEvaluationListener
import org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits
import java.util.concurrent.TimeUnit

/**
 * Stops one evaluation once it has run longer than [budgetMillis]. The size guard bounds each value, but a macro can
 * repeat an expensive function call many times (`lists.range(20).all(i, sets.contains(big, big))`), so the evaluation
 * also needs a time limit.
 *
 * This is the cooperative part of the limit only. cel-java calls this listener after every value it produces, so the
 * evaluation ends at the first value after the budget ran out, which is after the function call that was running then
 * has returned. One call can take far longer than the budget (`sets.equivalent` over two 9,000-element lists runs
 * about a second), so this listener alone does not bound how long a check takes. The orchestrator bounds that: it runs
 * the evaluation on a separate thread and stops waiting shortly after the budget.
 *
 * One instance per evaluation: the clock starts when it is created. Reading the clock is safe because the orchestrator
 * runs the evaluation inside `Workflow.sideEffect`, which records the outcome; a replay never evaluates again.
 *
 * @param nanoClock The clock to read (tests pass a fake one).
 */
internal class LoopConditionTimeBudget(
  private val budgetMillis: Long = LoopConditionLimits.MAX_EVALUATION_MILLIS,
  private val nanoClock: () -> Long = System::nanoTime
) : CelEvaluationListener {

  /** Thrown from the listener once the budget is spent. */
  class TimeBudgetExceeded(budgetMillis: Long) : EvaluationLimitExceeded(
    limitDescription = LoopConditionLimits.evaluationTimeExceededDetail(budgetMillis = budgetMillis)
  )

  private val startNanos: Long = nanoClock()
  private val budgetNanos: Long = TimeUnit.MILLISECONDS.toNanos(budgetMillis)

  override fun callback(expression: CelExpr, value: Any?) {
    val elapsedNanos = nanoClock() - startNanos
    if (elapsedNanos > budgetNanos) {
      throw TimeBudgetExceeded(budgetMillis = budgetMillis)
    }
  }
}

/** Runs every listener in [guards] for each value, in order; the first one that throws stops the evaluation. */
internal class CombinedEvaluationListener(private val guards: List<CelEvaluationListener>) : CelEvaluationListener {

  override fun callback(expression: CelExpr, value: Any?) {
    guards.forEach { guard -> guard.callback(expression, value) }
  }
}
