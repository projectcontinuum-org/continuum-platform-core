package org.projectcontinuum.core.api.server.service

import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scheduler must hold a stop back for the grace period (the run needs that time to handle its own stop signal)
 * and must survive a stop that throws (one failure must not silently drop every later cancel and terminate).
 *
 * The scheduler owns a real timer thread, so these tests use short real delays and wait on latches with a generous
 * upper bound instead of sleeping for a fixed time.
 */
class TimerWorkflowStopSchedulerTest {

  companion object {
    private const val SHORT_DELAY_MILLIS = 400L
    private const val CLOCK_TOLERANCE_MILLIS = 50L
    private const val MAX_WAIT_SECONDS = 10L
  }

  private val scheduler = TimerWorkflowStopScheduler()

  @Test
  fun `the stop does not run before the delay has passed`() {
    val stopRan = CountDownLatch(1)
    val scheduledAtNanos = System.nanoTime()
    val ranAfterMillis = AtomicLong(-1L)

    scheduler.schedule(delayMillis = SHORT_DELAY_MILLIS) {
      ranAfterMillis.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - scheduledAtNanos))
      stopRan.countDown()
    }

    assertEquals(
      expected = 1L,
      actual = stopRan.count,
      message = "The stop must not run as part of the schedule call"
    )
    assertTrue(
      actual = stopRan.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS),
      message = "The stop must run once the delay has passed"
    )
    assertTrue(
      actual = ranAfterMillis.get() >= SHORT_DELAY_MILLIS - CLOCK_TOLERANCE_MILLIS,
      message = "The stop ran after ${ranAfterMillis.get()} ms, before the $SHORT_DELAY_MILLIS ms delay"
    )
  }

  @Test
  fun `a stop that throws does not prevent later stops from running`() {
    val failingStopRan = CountDownLatch(1)
    val laterStopRan = CountDownLatch(1)

    scheduler.schedule(delayMillis = 0L) {
      failingStopRan.countDown()
      throw IllegalStateException("The workflow could not be stopped")
    }
    assertTrue(
      actual = failingStopRan.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS),
      message = "The failing stop must have run"
    )
    scheduler.schedule(delayMillis = 0L) {
      laterStopRan.countDown()
    }

    assertTrue(
      actual = laterStopRan.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS),
      message = "A stop scheduled after a failed one must still run"
    )
  }
}
