package org.projectcontinuum.core.commons.validation

import org.projectcontinuum.core.commons.validation.loop.LoopEdgeRules

/**
 * A workflow must be a directed acyclic graph. Kahn's algorithm over the real connections: the nodes left over are on
 * or behind a cycle, and are named in one error. Only when the plain graph has no cycle does the loop variant run
 * (each child loop collapsed to one vertex, [LoopEdgeRules.collapsedCycleIssues]), so a cycle is never reported twice.
 */
internal object CycleRule : WorkflowRule {

  override fun check(graph: CheckedGraph): RuleIssues {
    val cycle = nodesOnCycles(keys = graph.nodes.resolvedNodes.keys, edges = graph.edges.validEdges)
    if (cycle.isEmpty()) {
      return RuleIssues(errors = LoopEdgeRules.collapsedCycleIssues(scopes = graph.loopScopes, edges = graph.edges.validEdges))
    }
    val cycleIssue = WorkflowIssue(
      message = "The edges contain a cycle through nodes ${cycle.joinToString(", ") { key -> "'$key'" }}. A workflow must be a directed acyclic graph."
    )
    return RuleIssues(errors = listOf(cycleIssue))
  }

  /** Kahn's algorithm; returns the keys left over when the graph has a cycle, else an empty list. */
  private fun nodesOnCycles(keys: Set<String>, edges: List<GraphEdge>): List<String> {
    val indegree = keys.associateWith { 0 }.toMutableMap()
    val outgoing = keys.associateWith { mutableListOf<String>() }
    for (edge in edges) {
      outgoing.getValue(edge.from!!).add(edge.to!!)
      indegree[edge.to] = indegree.getValue(edge.to) + 1
    }
    val queue = ArrayDeque(indegree.filterValues { count -> count == 0 }.keys)
    while (queue.isNotEmpty()) {
      for (next in outgoing.getValue(queue.removeFirst())) {
        indegree[next] = indegree.getValue(next) - 1
        if (indegree[next] == 0) queue.addLast(next)
      }
    }
    return indegree.filterValues { count -> count > 0 }.keys.toList()
  }
}
