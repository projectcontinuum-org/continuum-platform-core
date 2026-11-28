package org.projectcontinuum.core.commons.validation.loop

/**
 * Why a loop condition is rejected ([isRuntime] false: found by the validator before a run) or why its check failed at
 * run time ([isRuntime] true). Every message about a condition starts with [reasonText], then the CEL message and the
 * 1-based column when known, so the validator, the loop runner and the workbench say the same thing.
 *
 * Validation codes are reported in this order; the first one that applies wins.
 */
enum class LoopConditionIssueCode(val reasonText: String, val isRuntime: Boolean) {
  EMPTY(reasonText = "is empty", isRuntime = false),
  TOO_LONG(reasonText = "is longer than ${LoopConditionLimits.MAX_EXPRESSION_LENGTH} characters", isRuntime = false),
  TOO_DEEP(
    reasonText = "nests brackets deeper than ${LoopConditionLimits.MAX_NESTING_DEPTH} levels",
    isRuntime = false
  ),
  SYNTAX(reasonText = "is not a valid CEL expression", isRuntime = false),
  CHECK_ERROR(reasonText = "does not type-check", isRuntime = false),
  NOT_BOOLEAN(reasonText = "does not return true or false", isRuntime = false),
  INVALID_LITERAL(reasonText = "contains an invalid regex, timestamp or duration literal", isRuntime = false),
  REBOUND_NAME(
    reasonText = "binds a name that is already bound in an enclosing scope, or binds vars",
    isRuntime = false
  ),
  MISSING_VARIABLE(reasonText = "reads a flow variable that is not set", isRuntime = true),
  EVALUATION_ERROR(reasonText = "failed to evaluate", isRuntime = true),
  LIMIT_EXCEEDED(reasonText = "exceeded a cost limit", isRuntime = true),
  NON_BOOLEAN_RESULT(reasonText = "returned a value that is not true or false", isRuntime = true)
}
