package org.projectcontinuum.core.loopcondition

import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome

/**
 * Builds condition issues with one message format, shared by the validator and the evaluator: the code's reason text,
 * then the CEL message (whitespace collapsed to single spaces, so multi-line cel-java messages stay on one line), then
 * `(column n)` when the position is known. Columns are 1-based code points.
 */
internal object LoopConditionIssues {

  fun invalid(
    code: LoopConditionIssueCode,
    detail: String? = null,
    zeroBasedOffset: Int? = null
  ): LoopConditionCheckResult.Invalid {
    val column = columnOf(zeroBasedOffset = zeroBasedOffset)
    return LoopConditionCheckResult.Invalid(code = code, message = message(code = code, detail = detail, column = column), column = column)
  }

  fun failed(
    code: LoopConditionIssueCode,
    detail: String? = null,
    zeroBasedOffset: Int? = null,
    variableName: String? = null
  ): LoopConditionOutcome.Failed {
    val column = columnOf(zeroBasedOffset = zeroBasedOffset)
    return LoopConditionOutcome.Failed(
      code = code,
      message = message(code = code, detail = detail, column = column),
      variableName = variableName,
      column = column
    )
  }

  /** An invalid text, reported as a failed check (the evaluator never runs it). */
  fun failedFrom(invalid: LoopConditionCheckResult.Invalid): LoopConditionOutcome.Failed =
    LoopConditionOutcome.Failed(code = invalid.code, message = invalid.message, column = invalid.column)

  private fun columnOf(zeroBasedOffset: Int?): Int? =
    zeroBasedOffset?.takeIf { offset -> offset >= 0 }?.let { offset -> offset + 1 }

  private fun message(code: LoopConditionIssueCode, detail: String?, column: Int?): String {
    val singleLineDetail = detail?.replace(Regex("\\s+"), " ")?.trim()
    val withDetail = if (singleLineDetail.isNullOrEmpty()) code.reasonText else "${code.reasonText}: $singleLineDetail"
    return if (column == null) withDetail else "$withDetail (column $column)"
  }
}
