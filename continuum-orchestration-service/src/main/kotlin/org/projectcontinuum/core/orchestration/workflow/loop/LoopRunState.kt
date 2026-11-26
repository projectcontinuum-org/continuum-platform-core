package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.validation.loop.CompiledLoopCondition

/** Where a loop is in its run. */
enum class LoopPhase { NOT_STARTED, ITERATING, FINISHED }

/**
 * The run state of one loop, owned by [LoopRunner]. Workflow-scoped and updated at the same points in a live run and
 * in a replay, so it is deterministic.
 *
 * [iteration] is the iteration running (or the last one that ran), 0 before the first. [carriedIntoIteration] are the
 * variables the members carried into the current iteration (empty in iteration 1). [finalStatus] is set when the loop
 * finished: SUCCESS, WARNING, FAILED or SKIPPED.
 */
class LoopRunState(val loopId: String) {
  var phase: LoopPhase = LoopPhase.NOT_STARTED
  var iteration: Int = 0
  var settings: LoopSettings? = null
  var compiledCondition: CompiledLoopCondition? = null
  var carriedIntoIteration: Map<String, Any> = emptyMap()
  var finalStatus: NodeStatus? = null

  val isFinished: Boolean get() = phase == LoopPhase.FINISHED

  /**
   * Whether [carriedIntoIteration] still applies to the inputs of the nodes that run now. While the loop iterates, its
   * entry members get it. After the loop finished normally (SUCCESS or WARNING) after at least one iteration, it is the
   * set the last iteration's entry members received: a node after the loop inherits through those members, so it must
   * see the same set, or it would lose a variable set only in an earlier iteration and fall back to the outside value.
   * A loop that ran no iteration, failed or was skipped passes nothing on.
   */
  val passesCarriedVariablesOn: Boolean
    get() = when (phase) {
      LoopPhase.ITERATING -> true
      LoopPhase.FINISHED -> iteration > 0 && (finalStatus == NodeStatus.SUCCESS || finalStatus == NodeStatus.WARNING)
      LoopPhase.NOT_STARTED -> false
    }
}
