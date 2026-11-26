package org.projectcontinuum.core.loopcondition

import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits

/**
 * The checks that run on the raw text before the CEL parser sees it: blank text, the length cap and the bracket nesting
 * cap. The parser would accept a 2,049-character text and a 33-deep nesting, and recurse on the latter, so these caps
 * are ours and run first. Lengths are counted in code points, like JSON Schema `maxLength`.
 */
internal object LoopConditionTextPrecheck {

  /** The issue for [text], or null when the text may be handed to the parser. */
  fun check(text: String): LoopConditionCheckResult.Invalid? {
    if (text.isBlank()) {
      return LoopConditionIssues.invalid(code = LoopConditionIssueCode.EMPTY)
    }
    val codePointLength = text.codePointCount(0, text.length)
    if (codePointLength > LoopConditionLimits.MAX_EXPRESSION_LENGTH) {
      return LoopConditionIssues.invalid(
        code = LoopConditionIssueCode.TOO_LONG,
        detail = "the text has $codePointLength characters",
        zeroBasedOffset = LoopConditionLimits.MAX_EXPRESSION_LENGTH
      )
    }
    val nesting = LoopConditionNestingDepth.measure(text = text)
    if (nesting.maxDepth > LoopConditionLimits.MAX_NESTING_DEPTH) {
      return LoopConditionIssues.invalid(
        code = LoopConditionIssueCode.TOO_DEEP,
        detail = "brackets are nested ${nesting.maxDepth} deep",
        zeroBasedOffset = nesting.offsetOfMaxDepth
      )
    }
    return null
  }
}
