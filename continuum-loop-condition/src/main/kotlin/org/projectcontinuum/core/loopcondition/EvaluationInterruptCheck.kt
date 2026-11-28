package org.projectcontinuum.core.loopcondition

import dev.cel.common.ast.CelExpr
import dev.cel.runtime.CelEvaluationListener

/**
 * Stops an evaluation whose thread was interrupted. The orchestrator runs each condition check on an evaluation thread
 * and waits only a bounded time; when it gives up it interrupts that thread, and this listener ends the abandoned
 * evaluation at the next value cel-java produces instead of letting it run to its end. A function call that is already
 * running cannot be stopped; the evaluation ends right after it returns. The abandoned outcome is discarded.
 *
 * The interrupt flag is left set, so the thread's owner still sees it.
 */
internal object EvaluationInterruptCheck : CelEvaluationListener {

  /** Thrown from the listener when the evaluating thread was interrupted. */
  class EvaluationInterrupted : EvaluationLimitExceeded(limitDescription = "the evaluation was stopped because its thread was interrupted")

  override fun callback(expression: CelExpr, value: Any?) {
    if (Thread.currentThread().isInterrupted) {
      throw EvaluationInterrupted()
    }
  }
}
