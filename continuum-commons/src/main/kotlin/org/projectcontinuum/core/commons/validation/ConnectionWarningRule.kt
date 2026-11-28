package org.projectcontinuum.core.commons.validation

/**
 * Warnings about missing connections, never errors: a node with no edge at all (when the workflow has more than one
 * node), and each declared input port with no incoming edge. The orchestrator skips a node with an unconnected input
 * at run time, so the [ValidationMode.RUN] wording says so. A loop is a box, never an edge endpoint, so it is never
 * "not connected".
 */
internal object ConnectionWarningRule : WorkflowRule {

  override fun check(graph: CheckedGraph): RuleIssues {
    val resolvedNodes = graph.nodes.resolvedNodes
    val edges = graph.edges.validEdges
    val warnings = resolvedNodes.values.flatMap { node ->
      val notConnected = notConnectedWarning(node = node, edges = edges, nodeCount = resolvedNodes.size, graph = graph)
      listOfNotNull(notConnected) + unfedInputWarnings(node = node, edges = edges, mode = graph.mode)
    }
    return RuleIssues(warnings = warnings)
  }

  private fun notConnectedWarning(node: ResolvedNode, edges: List<GraphEdge>, nodeCount: Int, graph: CheckedGraph): WorkflowIssue? {
    val connected = edges.any { edge -> edge.from == node.key || edge.to == node.key }
    if (connected || nodeCount <= 1 || graph.loopScopes.isLoop(key = node.key)) return null
    return WorkflowIssue(message = "Node '${node.key}' is not connected to any other node.", node = node.key)
  }

  private fun unfedInputWarnings(node: ResolvedNode, edges: List<GraphEdge>, mode: ValidationMode): List<WorkflowIssue> {
    val fedPorts = edges.filter { edge -> edge.to == node.key }.map { edge -> edge.toPort }.toSet()
    val consequence = when (mode) {
      ValidationMode.AUTHORING -> "The node may fail if it needs this input."
      ValidationMode.RUN -> "The node is skipped at run time, so nodes that depend on it do not run."
    }
    return (node.descriptor?.inputs.orEmpty().keys - fedPorts).map { port ->
      WorkflowIssue(message = "Node '${node.key}': input port '$port' has no incoming edge. $consequence", node = node.key)
    }
  }
}
