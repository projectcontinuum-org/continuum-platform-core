package org.projectcontinuum.core.orchestration.registration

import java.time.Duration

/**
 * A [RetryScheduler] on virtual time for tests: [schedule] queues the task, [runAll] runs the queue in delay order
 * (tasks scheduled while running are run too) and records every delay. [shutDown] drops pending tasks, like the
 * production executor's `shutdownNow`.
 */
class VirtualTimeScheduler : RetryScheduler {

  private class Pending(val dueAtMillis: Long, val task: () -> Unit)

  private val pending = mutableListOf<Pending>()
  private var nowMillis = 0L
  private var shutDown = false

  /** Every delay passed to [schedule], in call order. */
  val requestedDelays = mutableListOf<Duration>()

  val pendingCount: Int get() = pending.size

  override fun schedule(delay: Duration, task: () -> Unit) {
    if (shutDown) return
    requestedDelays += delay
    pending += Pending(dueAtMillis = nowMillis + delay.toMillis(), task = task)
  }

  fun runAll() {
    while (pending.isNotEmpty() && !shutDown) {
      val next = pending.minBy { entry -> entry.dueAtMillis }
      pending.remove(next)
      nowMillis = next.dueAtMillis
      next.task()
    }
  }

  /** Runs only the tasks due within [duration] from now. */
  fun advanceBy(duration: Duration) {
    val until = nowMillis + duration.toMillis()
    while (!shutDown) {
      val next = pending.filter { entry -> entry.dueAtMillis <= until }.minByOrNull { entry -> entry.dueAtMillis } ?: break
      pending.remove(next)
      nowMillis = next.dueAtMillis
      next.task()
    }
    nowMillis = until
  }

  fun shutDown() {
    shutDown = true
    pending.clear()
  }
}
