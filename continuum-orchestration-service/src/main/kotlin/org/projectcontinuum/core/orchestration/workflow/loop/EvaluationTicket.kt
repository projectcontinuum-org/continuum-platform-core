package org.projectcontinuum.core.orchestration.workflow.loop

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * One evaluation handed to the evaluation threads. Exactly one side wins the race between an evaluation thread
 * picking it up ([tryStart]) and the waiting check giving up on it first ([tryWithdraw]), so "did it start?" has one
 * true answer: a withdrawn evaluation never runs, and the log line and the outcome of a check always agree on it.
 */
class EvaluationTicket {

  private enum class State { WAITING, STARTED, WITHDRAWN }

  private val state = AtomicReference(State.WAITING)

  @Volatile
  private var startedAtNanos: Long? = null

  /** Called by the evaluation thread; false when the check has already given up on it (then it must not evaluate). */
  fun tryStart(): Boolean {
    val started = state.compareAndSet(State.WAITING, State.STARTED)
    if (started) startedAtNanos = System.nanoTime()
    return started
  }

  /** Called by the waiting check; false when an evaluation thread has already started it. */
  fun tryWithdraw(): Boolean = state.compareAndSet(State.WAITING, State.WITHDRAWN)

  /** How long the evaluation has been running; 0 when it has not started (or is just starting). */
  fun runningMillis(): Long {
    val startNanos = startedAtNanos ?: return 0
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
  }
}
