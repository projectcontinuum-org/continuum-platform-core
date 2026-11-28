package org.projectcontinuum.core.orchestration.workflow.loop

import io.temporal.workflow.Workflow
import org.projectcontinuum.core.commons.validation.loop.LoopBudget
import java.text.NumberFormat
import java.util.Locale

/** The current size of the run history. Production: [TEMPORAL], the workflow info; tests pass fixed numbers. */
fun interface RunHistory {
  /** The number of events and the bytes of the history so far. */
  fun current(): Pair<Long, Long>

  companion object {
    val TEMPORAL = RunHistory { Workflow.getInfo().historyLength to Workflow.getInfo().historySize }
  }
}

/**
 * Stops a loop before Temporal's history limit (51,200 events, 50 MiB) can end the whole run: before every iteration
 * the history is compared with [maxHistoryEvents] (about 78 % of the event limit) and [maxHistoryBytes] (40 MiB). When
 * it is over either, the loop fails, whatever `onLimit` says. Code constants, not configuration: workflow code cannot
 * read deployment settings deterministically; the thresholds can be lowered for tests.
 */
class LoopHistoryGuard(
  private val history: RunHistory = RunHistory.TEMPORAL,
  private val maxHistoryEvents: Long = MAX_HISTORY_EVENTS,
  private val maxHistoryBytes: Long = MAX_HISTORY_BYTES
) {

  companion object {
    /** The same value the validator's run-history warning uses. */
    const val MAX_HISTORY_EVENTS: Long = LoopBudget.MAX_HISTORY_EVENTS.toLong()
    const val MAX_HISTORY_BYTES: Long = 40L * 1024 * 1024
  }

  /** The failure message when iteration [iteration] of [loopId] must not start, or null when it may. */
  fun stopMessage(loopId: String, iteration: Int): String? {
    val (events, bytes) = history.current()
    val numbers = NumberFormat.getIntegerInstance(Locale.ROOT)
    val reason = when {
      events > maxHistoryEvents -> "the run history has ${numbers.format(events)} events"
      bytes > maxHistoryBytes -> "the run history has ${numbers.format(bytes)} bytes"
      else -> return null
    }
    return "Loop '$loopId' stopped before iteration $iteration: $reason and would reach Temporal's limit. " +
      "Lower maxIterations or move work out of the loop."
  }
}
