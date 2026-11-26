package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.validation.loop.LoopConditionEvaluator
import org.projectcontinuum.core.orchestration.workflow.FlowVariableMerger

/**
 * Runs the loops of one workflow run. The DAG executor asks it on every pass ([advance]) and passes its [gate] to the
 * readiness decisions, so the members of a loop run only while their loop is in an iteration and a node after the loop
 * waits until the loop has finished.
 *
 * Per loop, outermost first:
 * - It starts when every edge entering it has delivered; when one is broken the loop and everything inside are
 *   SKIPPED. At start the settings and the condition are checked again ([LoopStartCheck]); a problem fails the loop.
 * - `check: before` evaluates the condition first (false: zero iterations, members SKIPPED, loop SUCCESS);
 *   `check: after` starts iteration 1 at once.
 * - An iteration ends when nothing inside the loop is running or ready. A failed member fails the loop. When no node
 *   inside ran in the iteration (all skipped), the loop is SKIPPED: repeating it would change nothing. Otherwise the
 *   carried variables are updated and the condition is checked ([LoopConditionCheck], [ConditionOutcomePolicy]): true
 *   starts the next iteration after a reset ([LoopReset]) and the history guard ([LoopHistoryGuard]), or applies
 *   `onLimit` at `maxIterations`; false ends the loop with SUCCESS.
 * - A loop with no node inside it to run (empty, or holding only empty loops) runs no iteration and ends with a
 *   WARNING, without checking the condition.
 *
 * Statuses and messages go through [LoopStatusPublisher]. Deterministic: every decision depends only on the model,
 * the outputs and the recorded condition outcomes.
 */
class LoopRunner(
  private val context: LoopRunContext,
  private val evaluator: () -> LoopConditionEvaluator,
  private val startCheck: LoopStartCheck,
  private val conditionCheck: LoopConditionCheck,
  private val historyGuard: LoopHistoryGuard
) {

  private val states = LinkedHashMap<String, LoopRunState>()
  private val probe = LoopIterationProbe(context = context)
  private val reset = LoopReset(context = context)
  private val publisher = LoopStatusPublisher(context = context)

  private companion object {
    /** A loop with no node to run finishes at once with this warning, like the validator's "has no nodes inside it". */
    const val NO_NODE_TO_RUN_MESSAGE = "The loop has no nodes inside it to run; it ran no iteration"
  }

  private fun stateOf(loopId: String): LoopRunState = states.getOrPut(loopId) { LoopRunState(loopId = loopId) }

  /** The loop part of the readiness decisions for the current loop states. */
  fun gate(): LoopReadinessGate {
    val all = context.topology.loopIds.map { loopId -> stateOf(loopId = loopId) }
    return LoopReadinessGate(
      topology = context.topology,
      activeLoopIds = all.filter { state -> state.phase == LoopPhase.ITERATING }.map { state -> state.loopId }.toSet(),
      finishedLoopIds = all.filter { state -> state.isFinished }.map { state -> state.loopId }.toSet(),
      failedOrSkippedLoopIds = all
        .filter { state -> state.finalStatus == NodeStatus.FAILED || state.finalStatus == NodeStatus.SKIPPED }
        .map { state -> state.loopId }
        .toSet()
    )
  }

  /**
   * The carried variables for the inputs of the nodes that run now: per iterating loop the set carried into its
   * current iteration, and per loop that finished normally the set carried into its last iteration (see
   * [LoopRunState.passesCarriedVariablesOn]), so a node after the loop receives what the last iteration passed on.
   * A nested loop's state is dropped when its enclosing loop starts the next iteration, so its set goes with it.
   */
  fun carriedByLoopId(): Map<String, Map<String, Any>> =
    states.values
      .filter { state -> state.passesCarriedVariablesOn }
      .associate { state -> state.loopId to state.carriedIntoIteration }

  /** Starts, ends and restarts loops until nothing changes; true when anything changed. */
  fun advance(): Boolean {
    var changedAtAll = false
    do {
      val changed = context.topology.loopIds.any { loopId -> isReachable(loopId = loopId) && step(loopId = loopId) }
      changedAtAll = changedAtAll || changed
    } while (changed)
    return changedAtAll
  }

  /** A loop can act only while every loop around it is in an iteration. */
  private fun isReachable(loopId: String): Boolean =
    context.topology.enclosingLoops(nodeId = loopId).all { enclosingId -> stateOf(loopId = enclosingId).phase == LoopPhase.ITERATING }

  private fun step(loopId: String): Boolean {
    val state = stateOf(loopId = loopId)
    return when (state.phase) {
      LoopPhase.NOT_STARTED -> tryStart(state = state)
      LoopPhase.ITERATING -> tryEndIteration(state = state)
      LoopPhase.FINISHED -> false
    }
  }

  private fun tryStart(state: LoopRunState): Boolean {
    when (probe.inputState(loopId = state.loopId, gate = gate())) {
      LoopInputState.PENDING -> return false
      LoopInputState.BROKEN -> {
        skip(state = state)
        return true
      }
      LoopInputState.READY -> Unit
    }
    val properties = context.node(nodeId = state.loopId).data.properties
    val settings = when (val checked = startCheck.check(loopId = state.loopId, properties = properties)) {
      is LoopStartCheck.Result.Rejected -> {
        fail(state = state, message = checked.message, maxIterations = 1)
        return true
      }
      is LoopStartCheck.Result.Accepted -> checked.settings
    }
    state.settings = settings
    if (!probe.hasNodeToRun(loopId = state.loopId)) {
      finishWithoutIterations(state = state, status = NodeStatus.WARNING, message = NO_NODE_TO_RUN_MESSAGE)
      return true
    }
    state.compiledCondition = evaluator().compile(text = settings.condition)
    if (settings.check == ConditionCheckTiming.AFTER) {
      beginIteration(state = state, iteration = 1, carried = emptyMap())
      return true
    }
    val decision = checkCondition(state = state, carriedAfter = emptyMap(), checkPoint = ConditionCheckPoint.BeforeFirstIteration)
    when (decision) {
      LoopDecision.Continue -> beginIteration(state = state, iteration = 1, carried = emptyMap())
      LoopDecision.StopSuccess -> finishWithoutIterations(state = state, status = NodeStatus.SUCCESS, message = "Condition was false before the first iteration")
      is LoopDecision.StopWithWarning -> finishWithoutIterations(state = state, status = NodeStatus.WARNING, message = decision.message)
      is LoopDecision.Fail -> fail(state = state, message = decision.message, maxIterations = settings.maxIterations)
    }
    return true
  }

  private fun tryEndIteration(state: LoopRunState): Boolean {
    if (!probe.isIterationOver(loopId = state.loopId, gate = gate(), states = states)) return false
    val settings = state.settings!!
    val failedMember = probe.failedDescendant(loopId = state.loopId)
    if (failedMember != null) {
      fail(state = state, message = "Loop '${state.loopId}' stopped in iteration ${state.iteration}: node '$failedMember' failed.", maxIterations = settings.maxIterations)
      return true
    }
    if (!probe.anyNodeRanInIteration(loopId = state.loopId)) {
      skipBecauseNothingRan(state = state)
      return true
    }
    val carriedAfter = LoopCarriedVariables.afterIteration(
      loopId = state.loopId,
      loopTopology = context.topology,
      previouslyCarried = state.carriedIntoIteration,
      nodeToOutputsMap = context.nodeToOutputsMap
    )
    val checkPoint = ConditionCheckPoint.AfterIteration(iteration = state.iteration)
    when (val decision = checkCondition(state = state, carriedAfter = carriedAfter, checkPoint = checkPoint)) {
      LoopDecision.Continue -> continueOrStopAtLimit(state = state, carriedAfter = carriedAfter)
      LoopDecision.StopSuccess -> finish(state = state, status = NodeStatus.SUCCESS, message = "Finished after ${state.iteration} iterations")
      is LoopDecision.StopWithWarning -> finish(state = state, status = NodeStatus.WARNING, message = decision.message)
      is LoopDecision.Fail -> fail(state = state, message = decision.message, maxIterations = settings.maxIterations)
    }
    return true
  }

  private fun continueOrStopAtLimit(state: LoopRunState, carriedAfter: Map<String, Any>) {
    val settings = state.settings!!
    if (state.iteration < settings.maxIterations) {
      reset.beforeNextIteration(loopId = state.loopId)
      dropNestedLoopStates(loopId = state.loopId)
      beginIteration(state = state, iteration = state.iteration + 1, carried = carriedAfter)
      return
    }
    when (settings.onLimit) {
      LimitBehaviour.FAIL -> fail(
        state = state,
        message = "Loop '${state.loopId}' reached its limit of ${settings.maxIterations} iterations and the condition still holds.",
        maxIterations = settings.maxIterations
      )
      LimitBehaviour.STOP_WITH_WARNING -> finish(state = state, status = NodeStatus.WARNING, message = "Stopped at the limit of ${settings.maxIterations} iterations")
    }
  }

  private fun checkCondition(state: LoopRunState, carriedAfter: Map<String, Any>, checkPoint: ConditionCheckPoint): LoopDecision {
    val variables = FlowVariableMerger.endOfIterationVariables(
      continuumWorkflow = context.workflow,
      loopId = state.loopId,
      nodeToOutputsMap = context.nodeToOutputsMap,
      loopTopology = context.topology,
      carriedByLoopId = carriedByLoopId() + (state.loopId to carriedAfter)
    ).variables
    val outcome = conditionCheck.evaluate(
      evaluator = evaluator(),
      compiled = state.compiledCondition!!,
      variables = variables,
      check = ConditionCheckLabel(loopId = state.loopId, checkPoint = checkPoint)
    )
    return ConditionOutcomePolicy.decide(
      loopId = state.loopId,
      outcome = outcome,
      onNonBooleanResult = state.settings!!.onNonBooleanResult,
      checkPoint = checkPoint
    )
  }

  private fun beginIteration(state: LoopRunState, iteration: Int, carried: Map<String, Any>) {
    val settings = state.settings!!
    val guardMessage = historyGuard.stopMessage(loopId = state.loopId, iteration = iteration)
    if (guardMessage != null) {
      fail(state = state, message = guardMessage, maxIterations = settings.maxIterations)
      return
    }
    state.phase = LoopPhase.ITERATING
    state.iteration = iteration
    state.carriedIntoIteration = carried
    publisher.iterationStarted(loopId = state.loopId, iteration = iteration, maxIterations = settings.maxIterations)
  }

  private fun finish(state: LoopRunState, status: NodeStatus, message: String) {
    state.phase = LoopPhase.FINISHED
    state.finalStatus = status
    publisher.finished(loopId = state.loopId, status = status, message = message, carriedIntoLastIteration = state.carriedIntoIteration)
  }

  private fun finishWithoutIterations(state: LoopRunState, status: NodeStatus, message: String) {
    finish(state = state, status = status, message = message)
    skipDescendants(loopId = state.loopId)
  }

  private fun fail(state: LoopRunState, message: String, maxIterations: Int) {
    state.phase = LoopPhase.FINISHED
    state.finalStatus = NodeStatus.FAILED
    publisher.failed(loopId = state.loopId, message = message, iteration = state.iteration, maxIterations = maxIterations)
    skipDescendants(loopId = state.loopId)
  }

  private fun skip(state: LoopRunState) {
    state.phase = LoopPhase.FINISHED
    state.finalStatus = NodeStatus.SKIPPED
    context.setNodeStatus(context.node(nodeId = state.loopId), NodeStatus.SKIPPED)
    skipDescendants(loopId = state.loopId)
  }

  /**
   * No node inside the loop could run in this iteration (every one was skipped, for example for an unconnected
   * input). The next iteration would see the same inputs and variables, so the loop would only repeat nothing until
   * its limit, inside one workflow task where the history guard cannot see it. Like a node that cannot run, the loop
   * is SKIPPED, without checking the condition, and the nodes after it are skipped.
   */
  private fun skipBecauseNothingRan(state: LoopRunState) {
    state.phase = LoopPhase.FINISHED
    state.finalStatus = NodeStatus.SKIPPED
    publisher.skipped(loopId = state.loopId, message = "No node inside the loop could run in iteration ${state.iteration}")
    skipDescendants(loopId = state.loopId)
  }

  /** Marks the untouched nodes inside a loop that will not run again SKIPPED, nested loops included. */
  private fun skipDescendants(loopId: String) {
    publisher.skipUntouchedDescendants(loopId = loopId)
    context.topology.descendants(loopId = loopId)
      .filter { nodeId -> context.topology.isLoop(nodeId = nodeId) }
      .map { nestedId -> stateOf(loopId = nestedId) }
      .filter { nested -> !nested.isFinished }
      .forEach { nested ->
        nested.phase = LoopPhase.FINISHED
        nested.finalStatus = NodeStatus.SKIPPED
      }
  }

  /** Nested loops run again from the start in the next iteration of their enclosing loop. */
  private fun dropNestedLoopStates(loopId: String) {
    context.topology.descendants(loopId = loopId)
      .filter { nodeId -> context.topology.isLoop(nodeId = nodeId) }
      .forEach { nestedId -> states.remove(nestedId) }
  }
}
