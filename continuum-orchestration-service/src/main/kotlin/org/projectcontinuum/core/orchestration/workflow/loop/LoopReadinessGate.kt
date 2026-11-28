package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

/**
 * The loop part of the readiness decisions, used by
 * [org.projectcontinuum.core.orchestration.workflow.NodeReadiness]. Pure: answers depend only on [topology] and the
 * three loop sets the loop runner owns.
 *
 * - An edge that leaves a loop (source inside, target outside) waits for the loop: it is pending while the loop has not
 *   finished, and broken when the loop failed or was skipped. Only then is it judged by its source, as any edge. For
 *   nested loops every loop the edge leaves counts.
 * - A node inside a loop is eligible to run (or to be skipped by the generic rules) only while every loop around it
 *   is in an active iteration.
 *
 * [NONE] has no loops, so every edge is judged by its source and every node is eligible, exactly as before loops.
 */
data class LoopReadinessGate(
  val topology: LoopTopology,
  val activeLoopIds: Set<String> = emptySet(),
  val finishedLoopIds: Set<String> = emptySet(),
  val failedOrSkippedLoopIds: Set<String> = emptySet()
) {

  companion object {
    val NONE = LoopReadinessGate(topology = LoopTopology.NONE)
  }

  /** What the loops say about an edge before its source is looked at. */
  enum class LoopEdgeVerdict {
    /** No loop holds the edge back: judge it by its source. */
    JUDGE_BY_SOURCE,

    /** The edge leaves a loop that has not finished yet. */
    PENDING,

    /** The edge leaves a loop that failed or was skipped: it never carries data. */
    BROKEN
  }

  fun edgeVerdict(edge: ContinuumWorkflowModel.Edge): LoopEdgeVerdict {
    if (!topology.hasLoops()) return LoopEdgeVerdict.JUDGE_BY_SOURCE
    val leftLoopIds = loopsLeftBy(edge = edge)
    return when {
      leftLoopIds.isEmpty() -> LoopEdgeVerdict.JUDGE_BY_SOURCE
      leftLoopIds.any { loopId -> loopId in failedOrSkippedLoopIds } -> LoopEdgeVerdict.BROKEN
      leftLoopIds.all { loopId -> loopId in finishedLoopIds } -> LoopEdgeVerdict.JUDGE_BY_SOURCE
      else -> LoopEdgeVerdict.PENDING
    }
  }

  /** True when every loop around [nodeId] is in an active iteration (always true at the top level). */
  fun isEligible(nodeId: String): Boolean =
    topology.enclosingLoops(nodeId = nodeId).all { loopId -> loopId in activeLoopIds }

  /** The loops that contain the edge's source but not its target. */
  private fun loopsLeftBy(edge: ContinuumWorkflowModel.Edge): List<String> =
    topology.enclosingLoops(nodeId = edge.source).filter { loopId ->
      !topology.isInside(nodeId = edge.target, loopId = loopId)
    }
}
