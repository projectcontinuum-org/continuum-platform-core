package org.projectcontinuum.core.commons.validation.loop

/**
 * How much of Temporal's run history a loop uses, measured on the Temporal test server (the orchestration service's
 * `ContinuumWorkflowLoopHistoryBudgetTest` fails when the real cost drifts more than 20 % from these numbers). Every
 * iteration adds [EVENTS_PER_MEMBER_EXECUTION] events per node inside the loop (activity scheduled, started and
 * completed, the worker's progress signal, the workflow tasks around them) and [EVENTS_PER_CONDITION_CHECK] for the
 * recorded condition outcome. The orchestrator stops a loop at [MAX_HISTORY_EVENTS] (the same value as its history
 * guard), so the validator warns before a workflow can get there.
 */
object LoopBudget {
  const val EVENTS_PER_MEMBER_EXECUTION = 10
  const val EVENTS_PER_CONDITION_CHECK = 1
  const val MAX_HISTORY_EVENTS = 40_000

  /** The history events a loop with [memberCount] nodes inside it uses for [maxIterations] iterations. */
  fun eventsFor(maxIterations: Int, memberCount: Int): Long =
    maxIterations.toLong() * (memberCount.toLong() * EVENTS_PER_MEMBER_EXECUTION + EVENTS_PER_CONDITION_CHECK)

  /** True when running all [maxIterations] iterations could reach the orchestrator's history guard. */
  fun exceeds(maxIterations: Int, memberCount: Int): Boolean =
    eventsFor(maxIterations = maxIterations, memberCount = memberCount) > MAX_HISTORY_EVENTS

  /** The most iterations a loop with [memberCount] nodes inside it can run before the guard stops it. */
  fun ceilingFor(memberCount: Int): Long =
    MAX_HISTORY_EVENTS / (memberCount.toLong() * EVENTS_PER_MEMBER_EXECUTION + EVENTS_PER_CONDITION_CHECK)
}
