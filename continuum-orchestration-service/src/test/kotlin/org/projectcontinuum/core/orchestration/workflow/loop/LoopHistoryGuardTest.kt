package org.projectcontinuum.core.orchestration.workflow.loop

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The loop stops before the run history reaches Temporal's limits. */
class LoopHistoryGuardTest {

  @Test
  fun `under both thresholds the next iteration may start`() {
    val guard = LoopHistoryGuard(history = { 40_000L to 1_000L })

    assertNull(guard.stopMessage(loopId = "L", iteration = 812))
  }

  @Test
  fun `over the event threshold the loop stops with the event count`() {
    val guard = LoopHistoryGuard(history = { 40_012L to 1_000L })

    assertEquals(
      "Loop 'L' stopped before iteration 812: the run history has 40,012 events and would reach Temporal's limit. " +
        "Lower maxIterations or move work out of the loop.",
      guard.stopMessage(loopId = "L", iteration = 812)
    )
  }

  @Test
  fun `over the size threshold the loop stops with the byte count`() {
    val guard = LoopHistoryGuard(history = { 10L to LoopHistoryGuard.MAX_HISTORY_BYTES + 1 })

    assertEquals(true, guard.stopMessage(loopId = "L", iteration = 3)?.contains("41,943,041 bytes"))
  }
}
