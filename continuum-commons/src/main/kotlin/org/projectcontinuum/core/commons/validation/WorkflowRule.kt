package org.projectcontinuum.core.commons.validation

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.validation.loop.LoopScopes

/** The errors and warnings one rule found, each in the order the rule found them. */
internal class RuleIssues(
  val errors: List<WorkflowIssue> = emptyList(),
  val warnings: List<WorkflowIssue> = emptyList()
)

/**
 * A node the node checks accepted: its unique key, its nodeId (empty when none was given) and its registered
 * definition. For a registered node, [properties] are the properties as given and [propertyIssues] the errors the
 * property checks reported for it (so a later rule does not report the same property again).
 */
internal class ResolvedNode(
  val key: String,
  val nodeId: String,
  val descriptor: ContinuumWorkflowModel.NodeData?,
  val properties: Map<String, Any?> = emptyMap(),
  val propertyIssues: List<WorkflowIssue> = emptyList()
)

/**
 * What the node and edge checks decided about a workflow, read by every [WorkflowRule]: which keys exist
 * ([CheckedNodes.resolvedNodes]), which edges are real connections ([CheckedEdges.validEdges]) and the loop scopes.
 */
internal class CheckedGraph(
  val mode: ValidationMode,
  val nodes: CheckedNodes,
  val loopScopes: LoopScopes,
  val edges: CheckedEdges
)

/** One focused workflow rule. Pure: it reads the checked graph and returns what it found. */
internal fun interface WorkflowRule {
  fun check(graph: CheckedGraph): RuleIssues
}
