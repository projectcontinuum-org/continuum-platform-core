package org.projectcontinuum.core.orchestration.workflow.loop

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bounded wait for a condition evaluation: the caller (the workflow thread in production) always gets an answer
 * within the wait, slow evaluations are interrupted, evaluations that ignore the interrupt cannot pile up beyond the
 * pool, a check turned away because every thread is held is retried instead of failing, and shutting the runner
 * down never waits for them.
 */
class TimeBoxedConditionEvaluationRunnerTest {

  private val waitMillis = 100L

  /** A check whose evaluation ran this long when the wait ended counts as too slow (production: 200 of 250 ms). */
  private val budgetMillis = 60L
  private val check = ConditionCheckLabel(loopId = "L", checkPoint = ConditionCheckPoint.AfterIteration(iteration = 3))
  private val warnings: MutableList<String> = Collections.synchronizedList(mutableListOf())

  private fun runner(threads: Int = 2, queueCapacity: Int = 4) = TimeBoxedConditionEvaluationRunner(
    threads = threads,
    queueCapacity = queueCapacity,
    budgetMillis = budgetMillis,
    waitMillis = waitMillis,
    warn = { message -> warnings += message },
    workflowContext = { "workflow 'w-1' (run 'r-1')" }
  )

  /** An evaluation stuck in one long call: it ignores interrupts until [release] opens. */
  private fun stuckEvaluation(release: CountDownLatch, started: AtomicInteger): () -> LoopConditionOutcome = {
    started.incrementAndGet()
    while (release.count > 0) {
      Thread.onSpinWait()
    }
    LoopConditionOutcome.True
  }

  private fun elapsedMillisOf(block: () -> Unit): Long {
    val startNanos = System.nanoTime()
    block()
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
  }

  @Test
  fun `an evaluation that finishes in time returns its own outcome and logs nothing`() {
    runner().use { runner ->
      val outcome = runner.run(check = check) { LoopConditionOutcome.False }

      assertEquals(LoopConditionOutcome.False, outcome)
      assertTrue(warnings.isEmpty(), "$warnings")
    }
  }

  @Test
  fun `a slow evaluation fails with the time budget message, is interrupted and logs one warning without variable values`() {
    val interrupted = CountDownLatch(1)
    runner().use { runner ->
      var outcome: LoopConditionOutcome? = null
      val elapsedMillis = elapsedMillisOf {
        outcome = runner.run(check = check) {
          try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(10))
          } catch (stopped: InterruptedException) {
            interrupted.countDown()
          }
          LoopConditionOutcome.Failed(code = LoopConditionIssueCode.LIMIT_EXCEEDED, message = "secret-value-123")
        }
      }

      val failed = outcome as LoopConditionOutcome.Failed
      assertEquals(LoopConditionIssueCode.LIMIT_EXCEEDED, failed.code)
      assertEquals("exceeded a cost limit: the condition took longer than 200 ms to evaluate", failed.message)
      assertTrue(elapsedMillis < waitMillis + 200, "waited $elapsedMillis ms")
      assertTrue(interrupted.await(2, TimeUnit.SECONDS), "the abandoned evaluation must be interrupted")
      assertEquals(1, warnings.size, "$warnings")
      val warning = warnings.single()
      assertTrue(warning.contains("Loop 'L'") && warning.contains("in iteration 3") && warning.contains("workflow 'w-1' (run 'r-1')"), warning)
      assertTrue(!warning.contains("secret-value-123"), warning)
    }
  }

  @Test
  fun `a check that finds every thread held by abandoned evaluations records nothing and is retried, both queued and refused`() {
    val release = CountDownLatch(1)
    val started = AtomicInteger(0)
    runner(threads = 2, queueCapacity = 1).use { runner ->
      try {
        repeat(times = 2) {
          val outcome = runner.run(check = check, evaluation = stuckEvaluation(release = release, started = started))
          assertEquals(LoopConditionIssueCode.LIMIT_EXCEEDED, (outcome as LoopConditionOutcome.Failed).code)
        }
        assertEquals(2, started.get(), "both threads are now held by evaluations that ignore the interrupt")

        val queuedFailure = AtomicReference<Throwable>()
        val queuedCaller = thread(name = "queued-check") {
          try {
            runner.run(check = check, evaluation = stuckEvaluation(release = release, started = started))
          } catch (unavailable: ConditionEvaluationUnavailable) {
            queuedFailure.set(unavailable)
          }
        }
        waitUntil { runner.waitingChecks() == 1 }
        val refusedMillis = elapsedMillisOf {
          assertThrows<ConditionEvaluationUnavailable> {
            runner.run(check = check, evaluation = stuckEvaluation(release = release, started = started))
          }
        }
        queuedCaller.join(TimeUnit.SECONDS.toMillis(2))

        assertTrue(refusedMillis < waitMillis, "a full queue is refused at once, took $refusedMillis ms")
        assertTrue(queuedFailure.get() is ConditionEvaluationUnavailable, "the queued check must not get an outcome to record: ${queuedFailure.get()}")
        assertEquals(
          "no loop condition evaluation thread is free: all 2 are held by abandoned evaluations",
          queuedFailure.get().message
        )
        assertEquals(0, runner.waitingChecks(), "a check that could not start in time leaves the queue")
        assertEquals(2, started.get(), "no more evaluations run than there are threads")
        assertEquals(4, warnings.size, "one warning per check that did not get its own outcome: $warnings")
        assertTrue(warnings.drop(2).all { warning -> warning.contains("nothing is recorded and the workflow task is retried") }, "$warnings")
      } finally {
        release.countDown()
      }
    }
  }

  @Test
  fun `a check that gets a thread too late to run for the budget is retried, not failed as too slow`() {
    val release = CountDownLatch(1)
    val started = AtomicInteger(0)
    runner(threads = 1, queueCapacity = 1).use { runner ->
      try {
        runner.run(check = check, evaluation = stuckEvaluation(release = release, started = started))
        val lateFailure = AtomicReference<Throwable>()
        val lateCaller = thread(name = "late-check") {
          try {
            runner.run(check = check) {
              Thread.sleep(TimeUnit.SECONDS.toMillis(2))
              LoopConditionOutcome.True
            }
          } catch (unavailable: ConditionEvaluationUnavailable) {
            lateFailure.set(unavailable)
          }
        }
        waitUntil { runner.waitingChecks() == 1 }
        // The thread frees up shortly before the queued check's wait ends, so it starts but cannot use its budget.
        Thread.sleep(waitMillis - budgetMillis / 2)
        release.countDown()
        lateCaller.join(TimeUnit.SECONDS.toMillis(2))

        assertTrue(lateFailure.get() is ConditionEvaluationUnavailable, "a late start is not the condition's outcome: ${lateFailure.get()}")
        assertTrue(warnings.last().contains("nothing is recorded"), warnings.last())
        // A thread did start this check, so the message must say it started too late, not that no thread was free.
        val lateMessage = lateFailure.get().message.orEmpty()
        assertTrue(lateMessage.contains("too late to use its $budgetMillis ms budget"), lateMessage)
        assertTrue(!lateMessage.contains("no loop condition evaluation thread is free"), lateMessage)
      } finally {
        release.countDown()
      }
    }
  }

  @Test
  fun `closing the runner does not wait for a stuck evaluation, and a later check is unavailable instead of recorded`() {
    val release = CountDownLatch(1)
    val started = AtomicInteger(0)
    val runner = runner(threads = 1)
    try {
      runner.run(check = check, evaluation = stuckEvaluation(release = release, started = started))

      val closeMillis = elapsedMillisOf { runner.close() }

      assertTrue(closeMillis < 100, "close took $closeMillis ms")
      assertThrows<ConditionEvaluationUnavailable> { runner.run(check = check) { LoopConditionOutcome.True } }
    } finally {
      release.countDown()
    }
  }

  @Test
  fun `an evaluation interrupted by the shutdown is not reported as the condition's outcome`() {
    val runner = runner(threads = 1)
    val evaluationStarted = CountDownLatch(1)
    val failure = AtomicReference<Throwable>()
    val caller = thread(name = "check-during-shutdown") {
      try {
        runner.run(check = check) {
          evaluationStarted.countDown()
          try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(10))
          } catch (stopped: InterruptedException) {
            Thread.currentThread().interrupt()
          }
          LoopConditionOutcome.Failed(code = LoopConditionIssueCode.LIMIT_EXCEEDED, message = "stopped by the interrupt")
        }
      } catch (unavailable: ConditionEvaluationUnavailable) {
        failure.set(unavailable)
      }
    }
    evaluationStarted.await(2, TimeUnit.SECONDS)

    runner.close()
    caller.join(TimeUnit.SECONDS.toMillis(2))

    assertTrue(failure.get() is ConditionEvaluationUnavailable, "the caller must not get an outcome to record: ${failure.get()}")
  }

  @Test
  fun `evaluation threads are named daemon threads`() {
    runner(threads = 1).use { runner ->
      val evaluationThread = AtomicReference<Thread>()

      runner.run(check = check) {
        evaluationThread.set(Thread.currentThread())
        LoopConditionOutcome.True
      }

      assertTrue(evaluationThread.get().name.startsWith(TimeBoxedConditionEvaluationRunner.THREAD_NAME_PREFIX), evaluationThread.get().name)
      assertTrue(evaluationThread.get().isDaemon)
    }
  }

  private fun waitUntil(condition: () -> Boolean) {
    val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
    while (!condition() && System.nanoTime() < deadlineNanos) {
      Thread.sleep(1)
    }
    assertTrue(condition(), "condition not reached within 2 s")
  }
}
