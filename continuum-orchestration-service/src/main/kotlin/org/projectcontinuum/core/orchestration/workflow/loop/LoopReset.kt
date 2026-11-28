package org.projectcontinuum.core.orchestration.workflow.loop

/**
 * Clears what one iteration of a loop left behind, so the next iteration starts clean: for every node inside the loop
 * (nested loops included) its outputs, errors, status and progress; for every edge inside the loop its runtime data
 * (delivered or broken) and its animation. Nested loops start again from scratch (their run states are dropped by
 * [LoopRunner]). Edges entering or leaving the loop are untouched.
 */
internal class LoopReset(private val context: LoopRunContext) {

  fun beforeNextIteration(loopId: String) {
    context.topology.descendants(loopId = loopId).forEach { nodeId ->
      context.nodeToOutputsMap.remove(nodeId)
      context.nodeErrorsMap.remove(nodeId)
      val node = context.node(nodeId = nodeId)
      node.data.status = null
      node.data.nodeProgress = null
    }
    context.topology.edgesInside(loopId = loopId).forEach { edge ->
      edge.data = null
      edge.animated = false
    }
  }
}
