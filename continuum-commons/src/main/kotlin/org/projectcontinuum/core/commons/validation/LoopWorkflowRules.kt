package org.projectcontinuum.core.commons.validation

import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.validation.loop.LoopBudgetRule
import org.projectcontinuum.core.commons.validation.loop.LoopConditionRules
import org.projectcontinuum.core.commons.validation.loop.LoopConditionValidator
import org.projectcontinuum.core.commons.validation.loop.LoopStructureRules

/**
 * The loop rules as [WorkflowRule]s. The rules themselves live in `validation.loop`; these adapters only pick what
 * each one needs from the checked graph.
 */
internal object LoopWorkflowRules {

  /** Type, parent and parent-cycle errors of loops and their members. */
  val structureErrors = WorkflowRule { graph -> RuleIssues(errors = LoopStructureRules.errors(scopes = graph.loopScopes)) }

  /** The warning for a loop with no nodes inside it. */
  val emptyLoopWarnings = WorkflowRule { graph -> RuleIssues(warnings = LoopStructureRules.warnings(scopes = graph.loopScopes)) }

  /** The warning when a loop's `maxIterations` may exceed the run history. */
  val budgetWarnings = WorkflowRule { graph ->
    val maxIterationsByLoopKey = loopProperties(graph = graph).mapNotNull { loop ->
      loop.maxIterations?.let { maxIterations -> loop.loopKey to maxIterations }
    }.toMap()
    RuleIssues(warnings = LoopBudgetRule.warnings(scopes = graph.loopScopes, maxIterationsByLoopKey = maxIterationsByLoopKey))
  }

  /** The CEL condition of every loop, checked by [validator]. */
  fun conditionRule(validator: LoopConditionValidator): WorkflowRule {
    val conditionRules = LoopConditionRules(validator = validator)
    return WorkflowRule { graph ->
      val conditionIssues = conditionRules.check(loops = loopProperties(graph = graph))
      RuleIssues(errors = conditionIssues.errors, warnings = conditionIssues.warnings)
    }
  }

  /**
   * Every registered loop in node order, with its properties as the property checks validated them
   * ([ValidationMode.AUTHORING]: merged over the registered defaults; [ValidationMode.RUN]: as sent) and whether those
   * checks already reported its condition.
   */
  fun loopProperties(graph: CheckedGraph): List<LoopConditionRules.LoopCondition> =
    graph.nodes.resolvedNodes.values
      .filter { node -> node.nodeId == LoopNode.NODE_MODEL && node.descriptor != null }
      .map { node -> loopPropertiesOf(node = node, mode = graph.mode) }

  private fun loopPropertiesOf(node: ResolvedNode, mode: ValidationMode): LoopConditionRules.LoopCondition {
    val conditionProperty = LoopNodeDescriptor.CONDITION_PROPERTY
    val validated: Map<String, Any?> = when (mode) {
      ValidationMode.AUTHORING -> node.descriptor!!.properties + node.properties
      ValidationMode.RUN -> node.properties
    }
    return LoopConditionRules.LoopCondition(
      loopKey = node.key,
      condition = validated[conditionProperty],
      schemaRejected = node.propertyIssues.any { issue -> issue.property == conditionProperty },
      maxIterations = (validated[LoopNodeDescriptor.MAX_ITERATIONS_PROPERTY] as? Number)?.toInt()
    )
  }
}
