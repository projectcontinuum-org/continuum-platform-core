package org.projectcontinuum.core.commons.validation.loop

import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.validation.WorkflowIssue

/**
 * The loop condition rule of [org.projectcontinuum.core.commons.validation.WorkflowValidator]: every loop's
 * `condition` is checked by the injected [validator] (the cel-java one in the services; this module never depends on
 * the engine). An invalid condition is one ERROR on the loop's `condition` property; a valid condition that reads no
 * flow variable (`true`, `1 < 2`) never changes, so it gets the one condition WARNING a loop can have. A condition the
 * properties schema already rejected (missing, not a string, empty, too long) is not checked again, so a property never
 * gets two errors.
 */
internal class LoopConditionRules(private val validator: LoopConditionValidator) {

  /**
   * A loop and its property values as validated (AUTHORING: merged over the defaults; RUN: as sent); [maxIterations]
   * is used by the history budget warning.
   */
  data class LoopCondition(
    val loopKey: String,
    val condition: Any?,
    val schemaRejected: Boolean,
    val maxIterations: Int? = null
  )

  class Issues(val errors: List<WorkflowIssue>, val warnings: List<WorkflowIssue>)

  fun check(loops: List<LoopCondition>): Issues {
    val errors = mutableListOf<WorkflowIssue>()
    val warnings = mutableListOf<WorkflowIssue>()
    loops.filter { loop -> !loop.schemaRejected }.forEach { loop ->
      val text = loop.condition as? String ?: return@forEach
      when (val result = validator.check(text = text)) {
        is LoopConditionCheckResult.Invalid -> errors += WorkflowIssue(
          message = "Loop '${loop.loopKey}': the condition ${result.message}.",
          node = loop.loopKey,
          property = LoopNodeDescriptor.CONDITION_PROPERTY
        )
        is LoopConditionCheckResult.Valid -> if (result.readVariables.isEmpty()) {
          warnings += WorkflowIssue(
            message = "Loop '${loop.loopKey}': the condition reads no flow variable, so it never changes; the loop runs " +
              "until maxIterations and then follows onLimit.",
            node = loop.loopKey,
            property = LoopNodeDescriptor.CONDITION_PROPERTY
          )
        }
      }
    }
    return Issues(errors = errors, warnings = warnings)
  }
}
