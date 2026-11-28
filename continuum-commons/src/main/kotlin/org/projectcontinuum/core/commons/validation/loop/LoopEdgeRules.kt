package org.projectcontinuum.core.commons.validation.loop

import org.projectcontinuum.core.commons.validation.GraphEdge
import org.projectcontinuum.core.commons.validation.WorkflowIssue

/**
 * The edge rules of loops (a loop has no ports; no path may leave a loop and come back into it), used by [org.projectcontinuum.core.commons.validation.WorkflowValidator].
 *
 * - [loopEndpointIssue]: a loop has no ports, so an edge may not name one.
 * - [collapsedCycleIssues]: the cycle check per scope with every child loop collapsed to one vertex. A path that leaves a
 *   loop and comes back into it through an outside node (`X1 -> Y -> X2`) is such a cycle: `Y` would have to run
 *   between two iterations. Called only when the plain graph has no cycle, so every cycle found here passes through
 *   a collapsed loop.
 *
 * Pure: returns issues, changes nothing.
 */
internal object LoopEdgeRules {

  /** The issue for an edge whose source or target is a loop, or null. The source is named when both are loops. */
  fun loopEndpointIssue(label: String, edge: GraphEdge, scopes: LoopScopes): WorkflowIssue? {
    val loopEndpoint = listOfNotNull(edge.from, edge.to).firstOrNull { key -> scopes.isLoop(key = key) } ?: return null
    return WorkflowIssue(
      message = "Edge $label: '$loopEndpoint' is a loop. A loop has no ports; connect the nodes inside it instead.",
      edge = label
    )
  }

  fun collapsedCycleIssues(scopes: LoopScopes, edges: List<GraphEdge>): List<WorkflowIssue> =
    scopes.scopes.flatMap { scope -> cycleIssuesInScope(scope = scope, scopes = scopes, edges = edges) }

  private fun cycleIssuesInScope(scope: String?, scopes: LoopScopes, edges: List<GraphEdge>): List<WorkflowIssue> {
    val vertices = scopes.directChildren(scope = scope)
    val successors = collapsedSuccessors(scope = scope, vertices = vertices, scopes = scopes, edges = edges)
    val verticesOnCycles = KahnRemainder.of(vertices = vertices, successors = successors)
    return verticesOnCycles
      .filter { vertex -> scopes.isLoop(key = vertex) }
      .mapNotNull { loopKey ->
        val returnPath = ShortestCycle.through(start = loopKey, successors = successors) ?: return@mapNotNull null
        val outsideNode = returnPath[1]
        WorkflowIssue(
          message = "The edges leave loop '$loopKey' and come back into it through '$outsideNode'. A node outside a " +
            "loop cannot run between its iterations; move '$outsideNode' into '$loopKey' or remove one of the edges.",
          node = loopKey
        )
      }
  }

  /** Edges with both endpoints inside [scope], mapped to the direct children of [scope]; self-edges dropped. */
  private fun collapsedSuccessors(
    scope: String?,
    vertices: List<String>,
    scopes: LoopScopes,
    edges: List<GraphEdge>
  ): Map<String, List<String>> {
    val successors = vertices.associateWith { mutableListOf<String>() }
    for (edge in edges) {
      val from = scopes.directChildIn(scope = scope, key = edge.from ?: continue) ?: continue
      val to = scopes.directChildIn(scope = scope, key = edge.to ?: continue) ?: continue
      if (from == to) continue
      val fromSuccessors = successors[from] ?: continue
      if (to !in fromSuccessors) fromSuccessors += to
    }
    return successors
  }
}

/** Kahn's algorithm: the vertices left over (on or behind a cycle), in [vertices] order. */
internal object KahnRemainder {
  fun of(vertices: List<String>, successors: Map<String, List<String>>): List<String> {
    val indegree = vertices.associateWith { 0 }.toMutableMap()
    successors.values.flatten().forEach { target -> indegree[target] = indegree.getValue(target) + 1 }
    val queue = ArrayDeque(vertices.filter { vertex -> indegree.getValue(vertex) == 0 })
    while (queue.isNotEmpty()) {
      val vertex = queue.removeFirst()
      successors[vertex].orEmpty().forEach { next ->
        indegree[next] = indegree.getValue(next) - 1
        if (indegree.getValue(next) == 0) queue.addLast(next)
      }
    }
    return vertices.filter { vertex -> indegree.getValue(vertex) > 0 }
  }
}

/** Breadth-first search for the shortest path from a vertex back to itself. */
internal object ShortestCycle {
  /** `[start, v1, ..., start]`, or null when [start] is not on a cycle. */
  fun through(start: String, successors: Map<String, List<String>>): List<String>? {
    val previous = mutableMapOf<String, String>()
    val queue = ArrayDeque<String>()
    successors[start].orEmpty().forEach { next ->
      if (next !in previous) {
        previous[next] = start
        queue.addLast(next)
      }
    }
    while (queue.isNotEmpty()) {
      val vertex = queue.removeFirst()
      if (vertex == start) return pathTo(start = start, previous = previous)
      successors[vertex].orEmpty().forEach { next ->
        if (next !in previous) {
          previous[next] = vertex
          queue.addLast(next)
        }
      }
    }
    return null
  }

  private fun pathTo(start: String, previous: Map<String, String>): List<String> {
    val reversedPath = mutableListOf(start)
    var current = previous.getValue(start)
    while (current != start) {
      reversedPath += current
      current = previous.getValue(current)
    }
    reversedPath += start
    return reversedPath.asReversed()
  }
}
