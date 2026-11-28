package org.projectcontinuum.core.commons.validation

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.validation.loop.LoopEdgeRules
import org.projectcontinuum.core.commons.validation.loop.LoopScopes

/** The edges whose endpoints and ports all exist (the real connections), and what the checks found. */
internal class CheckedEdges(val validEdges: List<GraphEdge>, val issues: RuleIssues)

/**
 * The per-edge checks of [WorkflowValidator]: all four fields set, no loop as an endpoint (a loop has no ports, so
 * such an edge is one issue and its ports are not checked), existing source and target keys, no self edge, no
 * reserved port ids, ports the registered definitions declare, no edge listed twice and exactly one edge per input
 * port. Only edges that pass every check are returned as [CheckedEdges.validEdges], so the cycle check and the
 * connection warnings only see real connections. One instance checks one workflow.
 */
internal class EdgeChecks(private val resolvedNodes: Map<String, ResolvedNode>, private val loopScopes: LoopScopes) {

  private val errors = mutableListOf<WorkflowIssue>()
  private val validEdges = mutableListOf<GraphEdge>()
  private val seenLabels = mutableSetOf<String>()
  private val inputFedBy = mutableMapOf<String, String>()
  private val knownKeys = resolvedNodes.keys.joinToString(", ").ifEmpty { "(none)" }

  fun check(edges: List<GraphEdge>): CheckedEdges {
    edges.forEachIndexed { index, edge -> checkEdge(index = index, edge = edge) }
    return CheckedEdges(validEdges = validEdges, issues = RuleIssues(errors = errors))
  }

  private fun checkEdge(index: Int, edge: GraphEdge) {
    val label = "${edge.from}.${edge.fromPort} -> ${edge.to}.${edge.toPort}"
    if (edge.from.isNullOrBlank() || edge.fromPort.isNullOrBlank() || edge.to.isNullOrBlank() || edge.toPort.isNullOrBlank()) {
      errors += WorkflowIssue(message = "Edge #${index + 1} ($label) needs from, fromPort, to and toPort.", edge = label)
      return
    }
    val loopEndpointIssue = LoopEdgeRules.loopEndpointIssue(label = label, edge = edge, scopes = loopScopes)
    if (loopEndpointIssue != null) {
      errors += loopEndpointIssue
      return
    }
    val endpointsOk = checkEndpoints(label = label, edge = edge)
    val portsOk = checkPorts(label = label, edge = edge)
    if (!seenLabels.add(label)) {
      errors += WorkflowIssue(message = "Edge $label is listed more than once.", edge = label)
      return
    }
    val inputFreeOk = checkInputIsFree(label = label, edge = edge)
    if (endpointsOk && portsOk && inputFreeOk) validEdges += edge
  }

  /** Both keys exist and differ. Returns false after reporting what is wrong. */
  private fun checkEndpoints(label: String, edge: GraphEdge): Boolean {
    var ok = true
    if (edge.from !in resolvedNodes) {
      errors += WorkflowIssue(message = "Edge $label: source node key '${edge.from}' does not exist. Node keys: $knownKeys.", edge = label)
      ok = false
    }
    if (edge.to !in resolvedNodes) {
      errors += WorkflowIssue(message = "Edge $label: target node key '${edge.to}' does not exist. Node keys: $knownKeys.", edge = label)
      ok = false
    }
    if (edge.from == edge.to) {
      errors += WorkflowIssue(message = "Edge $label: a node cannot connect to itself.", edge = label)
      ok = false
    }
    return ok
  }

  /** Neither port id is reserved, and each one is declared by its node's registered definition. */
  private fun checkPorts(label: String, edge: GraphEdge): Boolean {
    // checkEdge has already rejected an edge without both ports.
    val fromPortReserved = NodeOutputSystemPort.isSystemPort(edge.fromPort.orEmpty())
    val toPortReserved = NodeOutputSystemPort.isSystemPort(edge.toPort.orEmpty())
    if (fromPortReserved) errors += ReservedIdRules.edgePortIssue(label = label, side = "source", portId = edge.fromPort)
    if (toPortReserved) errors += ReservedIdRules.edgePortIssue(label = label, side = "target", portId = edge.toPort)
    val outputDeclared = fromPortReserved || isDeclaredOutput(label = label, edge = edge)
    val inputDeclared = toPortReserved || isDeclaredInput(label = label, edge = edge)
    return !fromPortReserved && !toPortReserved && outputDeclared && inputDeclared
  }

  /** True when the source is unknown or unregistered (reported elsewhere) or declares [GraphEdge.fromPort]. */
  private fun isDeclaredOutput(label: String, edge: GraphEdge): Boolean {
    val source = resolvedNodes[edge.from] ?: return true
    val outputs = source.descriptor?.outputs.orEmpty().keys
    if (source.descriptor == null || edge.fromPort in outputs) return true
    errors += WorkflowIssue(
      message = "Edge $label: '${source.nodeId}' has no output port '${edge.fromPort}'. " +
        "Output ports: ${outputs.joinToString(", ").ifEmpty { "(none)" }}.",
      edge = label
    )
    return false
  }

  /** True when the target is unknown or unregistered (reported elsewhere) or declares [GraphEdge.toPort]. */
  private fun isDeclaredInput(label: String, edge: GraphEdge): Boolean {
    val target = resolvedNodes[edge.to] ?: return true
    val inputs = target.descriptor?.inputs.orEmpty().keys
    if (target.descriptor == null || edge.toPort in inputs) return true
    errors += WorkflowIssue(
      message = "Edge $label: '${target.nodeId}' has no input port '${edge.toPort}'. " +
        "Input ports: ${inputs.joinToString(", ").ifEmpty { "(none)" }}.",
      edge = label
    )
    return false
  }

  /** Each input port takes exactly one edge: the first edge into it wins, the later ones are errors. */
  private fun checkInputIsFree(label: String, edge: GraphEdge): Boolean {
    val input = "${edge.to}.${edge.toPort}"
    val previous = inputFedBy.putIfAbsent(input, "${edge.from}.${edge.fromPort}") ?: return true
    errors += WorkflowIssue(
      message = "Edge $label: input port '${edge.toPort}' of node '${edge.to}' is already fed by $previous. " +
        "Each input port takes exactly one edge.",
      edge = label
    )
    return false
  }
}
