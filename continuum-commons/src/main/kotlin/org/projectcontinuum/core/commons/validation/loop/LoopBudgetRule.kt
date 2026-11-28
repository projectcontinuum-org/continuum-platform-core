package org.projectcontinuum.core.commons.validation.loop

import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.validation.WorkflowIssue

/**
 * The run-history warning of [org.projectcontinuum.core.commons.validation.WorkflowValidator]: a loop whose
 * `maxIterations` times (nodes inside it plus the condition check) may exceed [LoopBudget] gets one warning on
 * `maxIterations`. A warning, not an error: most loops end long before their limit.
 */
internal object LoopBudgetRule {

  /** [maxIterationsByLoopKey]: each loop's `maxIterations` as validated (loops whose value is not a number are left out). */
  fun warnings(scopes: LoopScopes, maxIterationsByLoopKey: Map<String, Int>): List<WorkflowIssue> =
    maxIterationsByLoopKey.mapNotNull { (loopKey, maxIterations) ->
      val memberCount = memberCount(scopes = scopes, loopKey = loopKey)
      if (memberCount == 0 || !LoopBudget.exceeds(maxIterations = maxIterations, memberCount = memberCount)) return@mapNotNull null
      WorkflowIssue(
        message = "Loop '$loopKey': maxIterations $maxIterations x $memberCount nodes inside may exceed the run history " +
          "limit (about ${LoopBudget.ceilingFor(memberCount = memberCount)} iterations with $memberCount nodes inside). " +
          "The run stops with an error when the history is nearly full.",
        node = loopKey,
        property = LoopNodeDescriptor.MAX_ITERATIONS_PROPERTY
      )
    }

  /** The non-loop nodes inside [loopKey] at any depth. */
  private fun memberCount(scopes: LoopScopes, loopKey: String): Int {
    val pending = ArrayDeque(scopes.directChildren(scope = loopKey))
    var count = 0
    while (pending.isNotEmpty()) {
      val key = pending.removeFirst()
      if (scopes.isLoop(key = key)) pending.addAll(scopes.directChildren(scope = key)) else count += 1
    }
    return count
  }
}
