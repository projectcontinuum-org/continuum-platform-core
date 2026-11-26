package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome

/**
 * Runs one condition evaluation and returns its outcome. Production uses [TimeBoxedConditionEvaluationRunner], which
 * evaluates on a separate thread and gives up after a bounded wait, so the workflow thread is never blocked long
 * enough to trip Temporal's deadlock detector. [DIRECT] evaluates on the calling thread (tests of the loop logic).
 *
 * Called inside the recorded side effect of a condition check, so whatever it returns is written to the run history
 * and a replay never calls it again.
 */
fun interface ConditionEvaluationRunner {

  /**
   * Runs [evaluation] for the check described by [check]. Never blocks for long; an evaluation that started and did
   * not finish in time gives a failed outcome. Throws [ConditionEvaluationUnavailable] when this runner cannot
   * evaluate the check now for a reason that says nothing about the condition (stopped, interrupted, or no thread
   * free in time because abandoned slow evaluations hold them all).
   */
  fun run(check: ConditionCheckLabel, evaluation: () -> LoopConditionOutcome): LoopConditionOutcome

  companion object {
    /** Evaluates on the calling thread, with no time limit of its own. */
    val DIRECT = ConditionEvaluationRunner { _, evaluation -> evaluation() }
  }
}

/** Names one condition check in log messages: the loop and where in the loop it is checked. */
data class ConditionCheckLabel(val loopId: String, val checkPoint: ConditionCheckPoint)

/**
 * The check cannot be evaluated now, for a reason that is not about the condition: the runner has been stopped (the
 * orchestrator is shutting down), the waiting thread was interrupted, or every evaluation thread is held by slow
 * evaluations that earlier checks gave up on (of any run, this one included). So nothing may be recorded: it
 * propagates out of the side effect, Temporal fails the workflow task, and the check runs again when the task is
 * retried, normally once a thread is free or on a running orchestrator. Never catch it inside the side effect: a
 * caught one would be recorded as the loop's outcome.
 */
class ConditionEvaluationUnavailable(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
