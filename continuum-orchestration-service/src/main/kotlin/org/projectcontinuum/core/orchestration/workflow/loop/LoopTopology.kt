package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode

/**
 * The loop structure of one workflow run, built once from the model. Pure and immutable, so it is safe in
 * Temporal workflow code: every answer depends only on the model, and lists keep `nodes`/`edges` order.
 *
 * Membership follows `Node.parentNode`. The run's workflow passed the validator, so parents name loops and chains have
 * no cycle; defensively, a `parentNode` that is unknown, names a non-loop or closes a cycle is ignored (the node is
 * treated as top level), so this class never loops forever.
 */
class LoopTopology private constructor(
  private val nodeIdsInOrder: List<String>,
  private val edges: List<ContinuumWorkflowModel.Edge>,
  val loopIds: Set<String>,
  private val parentByNodeId: Map<String, String>
) {

  companion object {
    /** A topology with no loops: every query answers as for a loop-free workflow. */
    val NONE: LoopTopology = LoopTopology(
      nodeIdsInOrder = emptyList(),
      edges = emptyList(),
      loopIds = emptySet(),
      parentByNodeId = emptyMap()
    )

    fun of(workflow: ContinuumWorkflowModel): LoopTopology {
      val loopIds = workflow.nodes.filter { node -> LoopNode.isLoop(node = node) }.map { node -> node.id }.toSet()
      val candidateParents = workflow.nodes
        .filter { node -> node.parentNode != null && node.parentNode in loopIds }
        .associate { node -> node.id to node.parentNode!! }
      return LoopTopology(
        nodeIdsInOrder = workflow.nodes.map { node -> node.id },
        edges = workflow.edges,
        loopIds = loopIds,
        parentByNodeId = withoutCycles(parentByNodeId = candidateParents)
      )
    }

    /** Drops every link that is part of a parent cycle. */
    private fun withoutCycles(parentByNodeId: Map<String, String>): Map<String, String> {
      val nodeIdsOnCycles = parentByNodeId.keys.filter { nodeId ->
        val visited = mutableSetOf<String>()
        var current: String? = parentByNodeId[nodeId]
        while (current != null && current != nodeId && visited.add(current)) {
          current = parentByNodeId[current]
        }
        current == nodeId
      }.toSet()
      return parentByNodeId.filterKeys { nodeId -> nodeId !in nodeIdsOnCycles }
    }
  }

  private val descendantsByLoopId: Map<String, Set<String>> = loopIds.associateWith { loopId ->
    nodeIdsInOrder.filter { nodeId -> loopId in enclosingLoops(nodeId = nodeId) }.toSet()
  }

  fun hasLoops(): Boolean = loopIds.isNotEmpty()

  fun isLoop(nodeId: String): Boolean = nodeId in loopIds

  /** The loop [nodeId] is directly inside, or null at the top level. */
  fun innermostLoopOf(nodeId: String): String? = parentByNodeId[nodeId]

  /** The nodes directly inside [loopId] (members and nested loops), in `nodes` order. */
  fun directMembers(loopId: String): List<String> =
    nodeIdsInOrder.filter { nodeId -> parentByNodeId[nodeId] == loopId }

  /** Every node inside [loopId] at any depth, nested loops included, in `nodes` order. */
  fun descendants(loopId: String): List<String> {
    val descendantIds = descendantsByLoopId[loopId].orEmpty()
    return nodeIdsInOrder.filter { nodeId -> nodeId in descendantIds }
  }

  fun isInside(nodeId: String, loopId: String): Boolean = nodeId in descendantsByLoopId[loopId].orEmpty()

  /** The loops around [nodeId], outermost first (empty at the top level). */
  fun enclosingLoops(nodeId: String): List<String> {
    val innermostFirst = mutableListOf<String>()
    var current = parentByNodeId[nodeId]
    while (current != null) {
      innermostFirst += current
      current = parentByNodeId[current]
    }
    return innermostFirst.asReversed()
  }

  /** Edges with both endpoints inside [loopId], in `edges` order. */
  fun edgesInside(loopId: String): List<ContinuumWorkflowModel.Edge> =
    edges.filter { edge -> isInside(nodeId = edge.source, loopId = loopId) && isInside(nodeId = edge.target, loopId = loopId) }

  /** Edges from outside [loopId] to a node inside it, in `edges` order. */
  fun externalIncomingEdges(loopId: String): List<ContinuumWorkflowModel.Edge> =
    edges.filter { edge -> !isInside(nodeId = edge.source, loopId = loopId) && isInside(nodeId = edge.target, loopId = loopId) }

  /** Edges from a node inside [loopId] to a node outside it, in `edges` order. */
  fun outwardEdges(loopId: String): List<ContinuumWorkflowModel.Edge> =
    edges.filter { edge -> isInside(nodeId = edge.source, loopId = loopId) && !isInside(nodeId = edge.target, loopId = loopId) }

  /**
   * The nodes inside [loopId] that start an iteration: non-loop descendants with no incoming edge from another
   * descendant of [loopId], in `nodes` order.
   */
  fun entryMembers(loopId: String): List<String> {
    val insideTargets = edgesInside(loopId = loopId).map { edge -> edge.target }.toSet()
    return descendants(loopId = loopId).filter { nodeId -> !isLoop(nodeId = nodeId) && nodeId !in insideTargets }
  }

  private val entryMemberIdsByLoopId: Map<String, Set<String>> by lazy {
    loopIds.associateWith { loopId -> entryMembers(loopId = loopId).toSet() }
  }

  /** True when [nodeId] is one of [entryMembers] of [loopId] (cached, for the per-node variable merge). */
  fun isEntryMember(nodeId: String, loopId: String): Boolean = nodeId in entryMemberIdsByLoopId[loopId].orEmpty()

  /**
   * The non-loop descendants of [loopId] in run order: Kahn's algorithm over the edges inside the loop, ties broken by
   * `nodes` order. Used for the variable clash rule (the member later in this order wins).
   */
  fun topologicalOrder(loopId: String): List<String> {
    val members = descendants(loopId = loopId).filter { nodeId -> !isLoop(nodeId = nodeId) }
    val positionByNodeId = nodeIdsInOrder.withIndex().associate { (index, nodeId) -> nodeId to index }
    val insideEdges = edgesInside(loopId = loopId)
    val indegree = members.associateWith { 0 }.toMutableMap()
    insideEdges.forEach { edge -> indegree[edge.target] = indegree.getValue(edge.target) + 1 }
    val ready = java.util.PriorityQueue<String>(compareBy { nodeId -> positionByNodeId.getValue(nodeId) })
    ready.addAll(members.filter { nodeId -> indegree.getValue(nodeId) == 0 })
    val order = mutableListOf<String>()
    while (ready.isNotEmpty()) {
      val nodeId = ready.poll()
      order += nodeId
      insideEdges.filter { edge -> edge.source == nodeId }.forEach { edge ->
        indegree[edge.target] = indegree.getValue(edge.target) - 1
        if (indegree.getValue(edge.target) == 0) ready.add(edge.target)
      }
    }
    val unordered = members.filter { nodeId -> nodeId !in order }
    return order + unordered
  }
}
