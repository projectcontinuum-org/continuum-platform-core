package org.projectcontinuum.core.orchestration.workflow.loop

import io.temporal.workflow.Workflow
import io.temporal.workflow.unsafe.WorkflowUnsafe
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import org.slf4j.LoggerFactory
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs condition evaluations on a small pool of daemon threads and waits for each one at most [waitMillis]. The
 * evaluator stops itself at the time budget only between values, so one long function call (`sets.equivalent` over
 * two 9,000-element lists runs about a second) would otherwise block the workflow thread past Temporal's one-second
 * deadlock detector. Temporal then fails the workflow task, the outcome is never recorded, and every retry evaluates
 * again, so the run stays RUNNING for ever. With this runner the workflow thread waits at most [waitMillis] (250 ms),
 * whatever one call costs.
 *
 * - **In time:** the evaluation's own outcome.
 * - **Ran for the whole budget and is still running:** LIMIT_EXCEEDED "the condition took longer than 200 ms to
 *   evaluate", the same outcome the evaluator's own budget gives. The evaluation thread is interrupted; the evaluator
 *   checks the interrupt after every value and stops, and its late outcome is discarded. One WARN line names the
 *   loop, the check and the workflow run, never a variable value.
 * - **No free thread in time** (the queue is full, the check is still queued when the wait ends, or it got a thread
 *   so late that it has not run for [budgetMillis] yet): the threads are shared by every run on the orchestrator, so
 *   this says nothing about the condition: [ConditionEvaluationUnavailable]. Nothing is recorded and Temporal retries
 *   the workflow task, by which time the abandoned calls have normally returned (each frees its thread when its one
 *   cel-java call returns). Slow conditions can delay a loop's check, never fail it. A queued check is withdrawn and
 *   never runs, so at most [threads] abandoned evaluations exist at any time. Known limit: a run with more parallel
 *   loops with very slow conditions than there are threads starves itself, because every retry evaluates the checks
 *   of that task again; it keeps retrying (and stays RUNNING) until a retry finds enough free threads.
 * - **Stopped** ([close], at orchestrator shutdown) or the waiting thread was interrupted: [ConditionEvaluationUnavailable].
 *   Nothing is recorded and Temporal retries the workflow task, so shutting down never fails a user's loop.
 *
 * Replay safety: the runner is only called inside the recorded side effect of a condition check. Its outcome, the
 * timed-out one included, is written to the run history; a replay reads it from there and never calls the runner.
 * The bounded wait inside that side effect is the only thing the workflow thread blocks on.
 *
 * @param threads Evaluation threads. Four by default: an evaluation is CPU-bound and usually takes well under a
 *   millisecond, so four threads serve thousands of checks a second; more would only compete with the workflow
 *   threads for the orchestrator's CPUs; and up to three threads can be held by abandoned evaluations while checks
 *   still run on the fourth.
 * @param queueCapacity Checks that may wait for a thread. 200 by default, Temporal's default number of workflow tasks
 *   a worker runs at once, so a burst of checks from every task fits. Each waits at most [waitMillis] in all.
 * @param budgetMillis How long an evaluation must have run when the wait ends to count as too slow; the condition
 *   time budget.
 * @param waitMillis The longest the calling thread waits for one evaluation: the budget plus [WAIT_MARGIN_MILLIS].
 * @param warn Where the one WARN line per abandoned or turned-away check goes (tests capture it).
 * @param workflowContext Names the workflow run for that line.
 */
class TimeBoxedConditionEvaluationRunner(
  private val threads: Int = EVALUATION_THREADS,
  private val queueCapacity: Int = QUEUE_CAPACITY,
  private val budgetMillis: Long = LoopConditionLimits.MAX_EVALUATION_MILLIS,
  private val waitMillis: Long = budgetMillis + WAIT_MARGIN_MILLIS,
  private val warn: (String) -> Unit = { message -> LOGGER.warn(message) },
  private val workflowContext: () -> String = { currentWorkflowContext() }
) : ConditionEvaluationRunner, AutoCloseable {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(TimeBoxedConditionEvaluationRunner::class.java)

    /** Default number of evaluation threads (see the class documentation). */
    const val EVALUATION_THREADS = 4

    /** Default number of checks that may wait for a free evaluation thread. */
    const val QUEUE_CAPACITY = 200

    /**
     * Added to the 200 ms budget for the bounded wait: the evaluator's own budget fires at the first value after
     * 200 ms, so a condition made of many short calls ends on its own a few milliseconds later and reports the same
     * outcome. The total of 250 ms leaves three quarters of Temporal's one-second deadlock threshold for scheduling
     * delay on a busy orchestrator and the rest of the workflow task.
     */
    const val WAIT_MARGIN_MILLIS = 50L

    /** The thread name prefix of the evaluation threads. */
    const val THREAD_NAME_PREFIX = "loop-condition-evaluation-"

    private fun currentWorkflowContext(): String {
      if (!WorkflowUnsafe.isWorkflowThread()) return "outside a workflow"
      val info = Workflow.getInfo()
      return "workflow '${info.workflowId}' (run '${info.runId}')"
    }
  }

  private val executor = ThreadPoolExecutor(
    threads,
    threads,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(queueCapacity),
    EvaluationThreadFactory(),
    ThreadPoolExecutor.AbortPolicy()
  ).apply { prestartAllCoreThreads() }

  override fun run(check: ConditionCheckLabel, evaluation: () -> LoopConditionOutcome): LoopConditionOutcome {
    val ticket = EvaluationTicket()
    val task = FutureTask { evaluateUnlessWithdrawn(ticket = ticket, evaluation = evaluation) }
    try {
      executor.execute(task)
    } catch (rejected: RejectedExecutionException) {
      failIfStopped(cause = rejected)
      noFreeThread(
        check = check,
        why = "could not be queued: all $threads evaluation threads are busy and $queueCapacity checks are waiting",
        reason = "no loop condition evaluation thread is free: all $threads are busy and the queue is full"
      )
    }
    return awaitOutcome(task = task, ticket = ticket, check = check)
  }

  /** Checks waiting for a free evaluation thread (tests). */
  internal fun waitingChecks(): Int = executor.queue.size

  /**
   * Stops the pool without waiting: running evaluations are interrupted and the daemon threads end on their own, so
   * an evaluation stuck in a long call cannot hold up the orchestrator's shutdown. Checks after this throw
   * [ConditionEvaluationUnavailable].
   */
  override fun close() {
    executor.shutdownNow()
  }

  /** Runs on an evaluation thread. A withdrawn check's result is never read, so it is not evaluated at all. */
  private fun evaluateUnlessWithdrawn(ticket: EvaluationTicket, evaluation: () -> LoopConditionOutcome): LoopConditionOutcome? {
    if (!ticket.tryStart()) return null
    return evaluation()
  }

  private fun awaitOutcome(
    task: FutureTask<LoopConditionOutcome?>,
    ticket: EvaluationTicket,
    check: ConditionCheckLabel
  ): LoopConditionOutcome {
    val outcome = try {
      task.get(waitMillis, TimeUnit.MILLISECONDS)
    } catch (timeout: TimeoutException) {
      return giveUp(task = task, ticket = ticket, check = check, cause = timeout)
    } catch (interrupted: InterruptedException) {
      withdrawOrAbandon(task = task, ticket = ticket)
      Thread.currentThread().interrupt()
      throw ConditionEvaluationUnavailable(message = "interrupted while waiting for a loop condition evaluation", cause = interrupted)
    } catch (failed: ExecutionException) {
      throw failed.cause ?: failed
    }
    // An evaluation interrupted by a shutdown ends with a failed outcome that says nothing about the condition.
    failIfStopped(cause = null)
    return checkNotNull(outcome) { "a check that was not withdrawn has an outcome" }
  }

  private fun giveUp(
    task: FutureTask<LoopConditionOutcome?>,
    ticket: EvaluationTicket,
    check: ConditionCheckLabel,
    cause: TimeoutException
  ): LoopConditionOutcome {
    val runningMillis = ticket.runningMillis()
    val withdrawn = withdrawOrAbandon(task = task, ticket = ticket)
    failIfStopped(cause = cause)
    if (!withdrawn && runningMillis >= budgetMillis) {
      warn(describe(check = check, what = "did not finish within $waitMillis ms; it fails with LIMIT_EXCEEDED and its evaluation thread was interrupted"))
      return limitExceeded(detail = LoopConditionLimits.evaluationTimeExceededDetail())
    }
    if (withdrawn) {
      noFreeThread(
        check = check,
        why = "did not get an evaluation thread within $waitMillis ms: all $threads are busy",
        reason = "no loop condition evaluation thread is free: all $threads are held by abandoned evaluations"
      )
    }
    noFreeThread(
      check = check,
      why = "got an evaluation thread only $runningMillis ms before its $waitMillis ms wait ended: " +
        "the threads were busy",
      reason = "the loop condition evaluation started only $runningMillis ms before the wait ended, " +
        "too late to use its $budgetMillis ms budget: the evaluation threads were busy"
    )
  }

  /**
   * Withdraws a check no thread has picked up yet (it leaves the queue and never runs) and returns true. Otherwise
   * the evaluation has started: it is interrupted (it stops at its next value) and false is returned.
   */
  private fun withdrawOrAbandon(task: FutureTask<LoopConditionOutcome?>, ticket: EvaluationTicket): Boolean {
    if (ticket.tryWithdraw()) {
      task.cancel(false)
      executor.remove(task)
      return true
    }
    task.cancel(true)
    return false
  }

  /**
   * A check that could not get a thread in time: not the condition's outcome, so nothing is recorded and it is
   * retried. [why] completes the WARN line; [reason] is the exception message, which the SDK logs with each retry.
   */
  private fun noFreeThread(check: ConditionCheckLabel, why: String, reason: String): Nothing {
    warn(describe(check = check, what = "$why; nothing is recorded and the workflow task is retried"))
    throw ConditionEvaluationUnavailable(message = reason)
  }

  private fun failIfStopped(cause: Throwable?) {
    if (executor.isShutdown) {
      throw ConditionEvaluationUnavailable(message = "the loop condition evaluation threads have been stopped", cause = cause)
    }
  }

  private fun describe(check: ConditionCheckLabel, what: String): String =
    "Loop '${check.loopId}': the condition check ${check.checkPoint.phrase} of ${workflowContext()} $what"

  private fun limitExceeded(detail: String): LoopConditionOutcome.Failed =
    LoopConditionOutcome.Failed(
      code = LoopConditionIssueCode.LIMIT_EXCEEDED,
      message = "${LoopConditionIssueCode.LIMIT_EXCEEDED.reasonText}: $detail"
    )

  /** Named daemon threads, so a stuck evaluation never keeps the JVM alive. */
  private class EvaluationThreadFactory : ThreadFactory {
    private val nextNumber = AtomicInteger(1)

    override fun newThread(runnable: Runnable): Thread =
      Thread(runnable, "$THREAD_NAME_PREFIX${nextNumber.getAndIncrement()}").apply { isDaemon = true }
  }
}
