package org.projectcontinuum.core.commons.validation.loop

/** The checked result type of a valid condition. A `dyn` result is decided at run time (see `onNonBooleanResult`). */
enum class LoopConditionResultType { BOOL, DYN }

/** The outcome of checking a condition text. */
sealed interface LoopConditionCheckResult {

  /**
   * A valid condition. [readVariables] are the flow variable names it reads with a literal key (`vars["name"]`,
   * `vars.name`, `has(vars.name)`, `"name" in vars`, `vars[?"name"]`, `vars.?name`); a key computed at run time adds
   * nothing.
   */
  data class Valid(val resultType: LoopConditionResultType, val readVariables: Set<String>) : LoopConditionCheckResult

  /**
   * A rejected condition. [message] starts with [LoopConditionIssueCode.reasonText] of [code]; [column] is the 1-based
   * code point position of the problem, when known.
   */
  data class Invalid(val code: LoopConditionIssueCode, val message: String, val column: Int? = null) : LoopConditionCheckResult
}

/**
 * Checks a loop condition text against the CEL environment of loops. The implementation lives in
 * `continuum-loop-condition`; this module only owns the contract, so it never depends on the CEL engine. Never throws
 * for any input.
 */
fun interface LoopConditionValidator {
  fun check(text: String): LoopConditionCheckResult
}
