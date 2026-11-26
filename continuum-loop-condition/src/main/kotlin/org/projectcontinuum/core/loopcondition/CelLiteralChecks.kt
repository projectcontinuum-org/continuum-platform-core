package org.projectcontinuum.core.loopcondition

import dev.cel.common.CelAbstractSyntaxTree
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode

/**
 * Literal arguments that can be checked before a run: a regex given as a string literal (`matches`, the regex
 * functions), and `timestamp("...")` / `duration("...")` with a literal. A bad one would fail every run, so it is a
 * validation error. Uses cel-java's own literal validators on the shared environment.
 */
internal object CelLiteralChecks {

  fun check(checkedAst: CelAbstractSyntaxTree): LoopConditionCheckResult.Invalid? {
    val validationResult = LoopCelEnvironment.shared.literalValidator.validate(checkedAst)
    if (!validationResult.hasError()) return null
    val firstIssue = validationResult.errors.first()
    val offset = validationResult.source.getLocationOffset(firstIssue.sourceLocation).orElse(null)
    return LoopConditionIssues.invalid(
      code = LoopConditionIssueCode.INVALID_LITERAL,
      detail = firstIssue.message,
      zeroBasedOffset = offset
    )
  }
}
