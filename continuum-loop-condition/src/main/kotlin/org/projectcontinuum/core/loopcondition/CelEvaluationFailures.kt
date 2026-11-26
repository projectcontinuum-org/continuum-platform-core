package org.projectcontinuum.core.loopcondition

import dev.cel.common.CelErrorCode
import dev.cel.runtime.CelEvaluationException
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome

/**
 * Maps a cel-java evaluation error to a failed condition check:
 *
 * - `ATTRIBUTE_NOT_FOUND` for a key the condition reads with a literal name ([readVariables]) is MISSING_VARIABLE
 *   and names the variable (cel-java's message is the key for `vars["x"]`, and `key 'x' is not present in map.` for
 *   `vars.x`); a missing key of a nested map or a computed key is EVALUATION_ERROR with the CEL message.
 * - The comprehension iteration budget, the [LoopConditionSizeGuard], the [LoopConditionTimeBudget] (any
 *   [EvaluationLimitExceeded] cause) and the regex program size cap are LIMIT_EXCEEDED, naming the limit.
 * - Everything else (no matching overload, division by zero, overflow, bad format, ...) is EVALUATION_ERROR.
 *
 * cel-java writes the position as `evaluation error at <source>:<offset>: <message>`; the 0-based code point offset
 * becomes the 1-based column.
 */
internal object CelEvaluationFailures {

  private val locatedMessage = Regex("""^evaluation error at (.+?):(\d+): (.*)$""", RegexOption.DOT_MATCHES_ALL)
  private val unlocatedMessage = Regex("""^evaluation error: (.*)$""", RegexOption.DOT_MATCHES_ALL)
  private val selectedKey = Regex("""^key '(.*)' is not present in map\.?$""", RegexOption.DOT_MATCHES_ALL)
  private const val REGEX_SIZE_MESSAGE = "Regex pattern exceeds allowed program size"

  private class Located(val message: String, val offset: Int?)

  fun failed(exception: CelEvaluationException, readVariables: Set<String>): LoopConditionOutcome.Failed {
    val located = locate(message = exception.message.orEmpty())
    guardCause(exception = exception)?.let { exceededLimit ->
      return LoopConditionIssues.failed(
        code = LoopConditionIssueCode.LIMIT_EXCEEDED,
        detail = exceededLimit.limitDescription,
        zeroBasedOffset = located.offset
      )
    }
    return when {
      exception.errorCode == CelErrorCode.ITERATION_BUDGET_EXCEEDED -> LoopConditionIssues.failed(
        code = LoopConditionIssueCode.LIMIT_EXCEEDED,
        detail = "more than ${LoopConditionLimits.MAX_COMPREHENSION_ITERATIONS} comprehension iterations (${located.message})",
        zeroBasedOffset = located.offset
      )
      located.message.startsWith(REGEX_SIZE_MESSAGE) -> LoopConditionIssues.failed(
        code = LoopConditionIssueCode.LIMIT_EXCEEDED,
        detail = "the regular expression is larger than ${LoopConditionLimits.MAX_REGEX_PROGRAM_SIZE} (${located.message})",
        zeroBasedOffset = located.offset
      )
      exception.errorCode == CelErrorCode.ATTRIBUTE_NOT_FOUND -> attributeNotFound(located = located, readVariables = readVariables)
      else -> LoopConditionIssues.failed(
        code = LoopConditionIssueCode.EVALUATION_ERROR,
        detail = located.message,
        zeroBasedOffset = located.offset
      )
    }
  }

  /** A [StackOverflowError] at evaluation: a pathological expression the validator let through. */
  fun nestedTooDeeply(): LoopConditionOutcome.Failed =
    LoopConditionIssues.failed(code = LoopConditionIssueCode.LIMIT_EXCEEDED, detail = "the evaluation nested too deeply")

  /** Any other exception from cel-java: never thrown into workflow code. */
  fun unexpected(exception: RuntimeException): LoopConditionOutcome.Failed =
    LoopConditionIssues.failed(
      code = LoopConditionIssueCode.EVALUATION_ERROR,
      detail = exception.message ?: exception.javaClass.simpleName
    )

  private fun attributeNotFound(located: Located, readVariables: Set<String>): LoopConditionOutcome.Failed {
    val key = selectedKey.matchEntire(located.message)?.groupValues?.get(1) ?: located.message
    if (key !in readVariables) {
      return LoopConditionIssues.failed(
        code = LoopConditionIssueCode.EVALUATION_ERROR,
        detail = located.message,
        zeroBasedOffset = located.offset
      )
    }
    return LoopConditionIssues.failed(
      code = LoopConditionIssueCode.MISSING_VARIABLE,
      detail = "'$key'",
      zeroBasedOffset = located.offset,
      variableName = key
    )
  }

  private fun locate(message: String): Located {
    locatedMessage.matchEntire(message)?.let { match ->
      return Located(message = match.groupValues[3], offset = match.groupValues[2].toIntOrNull())
    }
    unlocatedMessage.matchEntire(message)?.let { match -> return Located(message = match.groupValues[1], offset = null) }
    return Located(message = message, offset = null)
  }

  private fun guardCause(exception: Throwable): EvaluationLimitExceeded? {
    var current: Throwable? = exception
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
      if (current is EvaluationLimitExceeded) return current
      current = current.cause
    }
    return null
  }
}
