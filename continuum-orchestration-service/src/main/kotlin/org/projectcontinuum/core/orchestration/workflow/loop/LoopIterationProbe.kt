package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.EdgeStatus
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.orchestration.workflow.NodeReadiness

/** What the edges entering a loop say about starting it. */
enum class LoopInputState { PENDING, READY, BROKEN }

/**
 * Pure questions [LoopRunner] asks about the run: may a loop start, is an iteration over, did a member fail. Reads the
 * [context] and the [gate] of the current loop states; changes nothing.
 */
internal class LoopIterationProbe(private val context: LoopRunContext) {

  /** BROKEN when an edge entering [loopId] will never deliver, PENDING while one has not settled, else READY. */
  fun inputState(loopId: String, gate: LoopReadinessGate): LoopInputState {
    val incoming = context.topology.externalIncomingEdges(loopId = loopId)
    if (incoming.isEmpty()) return LoopInputState.READY
    val statusByEdge = NodeReadiness.getEdgeStatuses(
      continuumWorkflow = context.workflow,
      nodeOutputMap = context.nodeToOutputsMap,
      nodeErrorsMap = context.nodeErrorsMap,
      loopGate = gate
    ).toMap()
    val statuses = incoming.map { edge -> statusByEdge[edge] }
    return when {
      statuses.any { status -> status == EdgeStatus.BROKEN } -> LoopInputState.BROKEN
      statuses.any { status -> status == null } -> LoopInputState.PENDING
      else -> LoopInputState.READY
    }
  }

  /**
   * An iteration of [loopId] is over when no node inside it is scheduled or running, none is ready to run, and every
   * loop nested in it has finished.
   */
  fun isIterationOver(loopId: String, gate: LoopReadinessGate, states: Map<String, LoopRunState>): Boolean {
    val descendants = context.topology.descendants(loopId = loopId)
    val inFlight = descendants.any { nodeId ->
      val status = context.node(nodeId = nodeId).data.status
      !context.topology.isLoop(nodeId = nodeId) && (status == NodeStatus.SCHEDULED || status == NodeStatus.BUSY)
    }
    if (inFlight) return false
    val nestedUnfinished = descendants.any { nodeId -> context.topology.isLoop(nodeId = nodeId) && states[nodeId]?.isFinished != true }
    if (nestedUnfinished) return false
    val descendantIds = descendants.toSet()
    return NodeReadiness.getReadyNodes(
      continuumWorkflow = context.workflow,
      nodeOutputMap = context.nodeToOutputsMap,
      nodeErrorsMap = context.nodeErrorsMap,
      loopGate = gate
    ).none { node -> node.id in descendantIds }
  }

  /** The first node inside [loopId] (in `nodes` order) that failed in this iteration, nested loops included. */
  fun failedDescendant(loopId: String): String? =
    context.topology.descendants(loopId = loopId).firstOrNull { nodeId -> context.nodeErrorsMap.containsKey(nodeId) }

  /**
   * Whether [loopId] has any node inside it that can run, at any depth. A loop that holds nothing, or only loops that
   * hold nothing, has nothing to repeat.
   */
  fun hasNodeToRun(loopId: String): Boolean =
    context.topology.descendants(loopId = loopId).any { nodeId -> !context.topology.isLoop(nodeId = nodeId) }

  /**
   * Whether any node inside [loopId] (at any depth, loops not counted) produced outputs or an error in the iteration
   * that just ended. The runner clears the members' outputs and errors between iterations, so the maps hold exactly
   * this iteration's results.
   */
  fun anyNodeRanInIteration(loopId: String): Boolean =
    context.topology.descendants(loopId = loopId).any { nodeId ->
      !context.topology.isLoop(nodeId = nodeId) &&
        (context.nodeToOutputsMap.containsKey(nodeId) || context.nodeErrorsMap.containsKey(nodeId))
    }
}
