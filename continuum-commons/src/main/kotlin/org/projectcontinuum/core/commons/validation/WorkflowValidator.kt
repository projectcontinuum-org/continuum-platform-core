package org.projectcontinuum.core.commons.validation

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.validation.loop.LoopBudgetRule
import org.projectcontinuum.core.commons.validation.loop.LoopConditionRules
import org.projectcontinuum.core.commons.validation.loop.LoopConditionValidator
import org.projectcontinuum.core.commons.validation.loop.LoopEdgeRules
import org.projectcontinuum.core.commons.validation.loop.LoopScopes
import org.projectcontinuum.core.commons.validation.loop.LoopStructureRules

/**
 * The one workflow checker: unique node keys, registered node ids, edge endpoints and port names, no self or duplicate
 * edges, exactly one edge per input port, no cycles (Kahn's algorithm) and each node's properties against the
 * registered schema. Node keys and port ids must not start with `$` (reserved for system keys such as `$error` and
 * `$variable`, see [NodeOutputSystemPort.isSystemPort]). Every problem is collected, not just the first, so a caller
 * can fix them all in one pass.
 *
 * Everything about a node comes from [descriptors], never from the submitted workflow. A client-sent
 * `propertiesSchema` or port list is client-controlled; in [ValidationMode.RUN] a difference from the registered one
 * is a warning, and the registered one is what gets validated.
 *
 * Loops (nodeId [org.projectcontinuum.core.commons.model.LoopNode.NODE_MODEL]) are checked by the collaborators in
 * `validation.loop`: [LoopStructureRules] (type, parent, parent cycles, empty loop) and [LoopEdgeRules] (no edge to or
 * from a loop, the cycle check with each loop collapsed to one vertex), [LoopConditionRules] when a
 * [conditionValidator] is given (the CEL condition; one error, or the warning for a condition that reads no flow
 * variable) and [LoopBudgetRule] (a warning when `maxIterations` may exceed the run history). A workflow without loops
 * gives exactly the same issues as before. Without a [conditionValidator] (callers that do not have the CEL engine)
 * conditions are only checked by the schema.
 *
 * Warnings (nodes with no edges, input ports with no incoming edge, client definitions that differ from the
 * registry, a loop with no nodes inside) never make a workflow invalid. A node with an unconnected declared input is
 * not an error: the orchestrator marks it SKIPPED at run time.
 *
 * This class only orchestrates: [NodeChecks] and [EdgeChecks] decide which nodes and edges are real, then each
 * [WorkflowRule] in [graphRules] reads that result. Errors and warnings are reported in that order (node checks,
 * condition, loop structure, edge checks, cycles, connections, empty loops, run history), which callers show as is.
 */
class WorkflowValidator(
  conditionValidator: LoopConditionValidator? = null,
  private val descriptors: NodeDescriptorSource
) {

  /** The checker without a condition validator (the trailing-lambda form `WorkflowValidator { nodeModel -> ... }`). */
  constructor(descriptors: NodeDescriptorSource) : this(conditionValidator = null, descriptors = descriptors)

  /** The condition rule runs right after the node checks, so its issues follow the property errors of the loop. */
  private val conditionRules: List<WorkflowRule> = listOfNotNull(
    conditionValidator?.let { validator -> LoopWorkflowRules.conditionRule(validator = validator) }
  )

  /** The rules that run once nodes and edges are checked, in reporting order. */
  private val graphRules: List<WorkflowRule> = listOf(
    LoopWorkflowRules.structureErrors,
    WorkflowRule { graph -> graph.edges.issues },
    CycleRule,
    ConnectionWarningRule,
    LoopWorkflowRules.emptyLoopWarnings,
    LoopWorkflowRules.budgetWarnings
  )

  fun validate(nodes: List<GraphNode>, edges: List<GraphEdge>, mode: ValidationMode): WorkflowValidationResult {
    val checkedNodes = NodeChecks(descriptors = descriptors, mode = mode).check(nodes = nodes)
    val loopScopes = LoopScopes.of(graphNodes = nodes, acceptedKeys = checkedNodes.resolvedNodes.keys)
    val checkedEdges = EdgeChecks(resolvedNodes = checkedNodes.resolvedNodes, loopScopes = loopScopes).check(edges = edges)
    val graph = CheckedGraph(mode = mode, nodes = checkedNodes, loopScopes = loopScopes, edges = checkedEdges)

    val ruleIssues = listOf(checkedNodes.issues) + (conditionRules + graphRules).map { rule -> rule.check(graph = graph) }
    return WorkflowValidationResult(
      errors = ruleIssues.flatMap { issues -> issues.errors },
      warnings = ruleIssues.flatMap { issues -> issues.warnings }
    )
  }
}
