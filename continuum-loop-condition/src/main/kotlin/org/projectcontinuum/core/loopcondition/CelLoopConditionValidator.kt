package org.projectcontinuum.core.loopcondition

import dev.cel.common.CelAbstractSyntaxTree
import dev.cel.common.CelValidationResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionResultType
import org.projectcontinuum.core.commons.validation.loop.LoopConditionValidator

/**
 * The cel-java [LoopConditionValidator]: the authoritative check of a loop condition, for the api-server's start,
 * validate and MCP tools and for the orchestrator's re-check at loop start. The steps run in this order and the first
 * problem wins, so every caller reports the same code for the same text:
 *
 * 1. text pre-checks ([LoopConditionTextPrecheck]): EMPTY, TOO_LONG, TOO_DEEP;
 * 2. parse: SYNTAX;
 * 3. type-check against `vars: map(string, dyn)` and the enabled libraries: CHECK_ERROR (unknown names and functions,
 *    `now()`, no matching overload on known types);
 * 4. result type ([ConditionResultTypeRule]): NOT_BOOLEAN;
 * 5. regex, timestamp and duration literals ([CelLiteralChecks]): INVALID_LITERAL;
 * 6. re-bound names ([ReboundNameRule]): REBOUND_NAME;
 * 7. the statically read variable names ([ReadVariableNames]) of a valid condition.
 *
 * A [StackOverflowError] or an unexpected cel-java exception is reported as SYNTAX: this class never throws.
 * Stateless; one instance is shared ([LoopConditionValidators.default]).
 */
class CelLoopConditionValidator internal constructor(
  private val environment: LoopCelEnvironment
) : LoopConditionValidator {

  constructor() : this(environment = LoopCelEnvironment.shared)

  /** A checked condition, kept for the evaluator so it does not check the text twice. */
  internal sealed interface Analysis {
    class Accepted(
      val checkedAst: CelAbstractSyntaxTree,
      val resultType: LoopConditionResultType,
      val readVariables: Set<String>
    ) : Analysis

    class Rejected(val issue: LoopConditionCheckResult.Invalid) : Analysis
  }

  override fun check(text: String): LoopConditionCheckResult = when (val analysis = analyze(text = text)) {
    is Analysis.Accepted -> LoopConditionCheckResult.Valid(resultType = analysis.resultType, readVariables = analysis.readVariables)
    is Analysis.Rejected -> analysis.issue
  }

  internal fun analyze(text: String): Analysis {
    LoopConditionTextPrecheck.check(text = text)?.let { precheckIssue -> return Analysis.Rejected(issue = precheckIssue) }
    return try {
      analyzeWithCel(text = text)
    } catch (stackOverflow: StackOverflowError) {
      rejected(code = LoopConditionIssueCode.SYNTAX, detail = "the expression is nested too deeply")
    } catch (celFailure: RuntimeException) {
      rejected(code = LoopConditionIssueCode.SYNTAX, detail = celFailure.message ?: celFailure.javaClass.simpleName)
    }
  }

  private fun analyzeWithCel(text: String): Analysis {
    val parsed = environment.parse(text = text)
    if (parsed.hasError()) return rejectedBy(code = LoopConditionIssueCode.SYNTAX, result = parsed)
    val checked = environment.check(parsedAst = parsed.ast)
    if (checked.hasError()) return rejectedBy(code = LoopConditionIssueCode.CHECK_ERROR, result = checked)
    val checkedAst = checked.ast
    val resultType = when (val verdict = ConditionResultTypeRule.check(checkedAst = checkedAst)) {
      is ConditionResultTypeRule.Verdict.Rejected -> return Analysis.Rejected(issue = verdict.issue)
      is ConditionResultTypeRule.Verdict.Accepted -> verdict.resultType
    }
    CelLiteralChecks.check(checkedAst = checkedAst, environment = environment)?.let { literalIssue ->
      return Analysis.Rejected(issue = literalIssue)
    }
    ReboundNameRule.check(checkedAst = checkedAst)?.let { reboundIssue -> return Analysis.Rejected(issue = reboundIssue) }
    return Analysis.Accepted(
      checkedAst = checkedAst,
      resultType = resultType,
      readVariables = ReadVariableNames.of(checkedAst = checkedAst)
    )
  }

  private fun rejectedBy(code: LoopConditionIssueCode, result: CelValidationResult): Analysis.Rejected {
    val firstIssue = result.errors.first()
    val offset = result.source.getLocationOffset(firstIssue.sourceLocation).orElse(null)
    return rejected(code = code, detail = firstIssue.message, zeroBasedOffset = offset)
  }

  private fun rejected(code: LoopConditionIssueCode, detail: String, zeroBasedOffset: Int? = null): Analysis.Rejected =
    Analysis.Rejected(issue = LoopConditionIssues.invalid(code = code, detail = detail, zeroBasedOffset = zeroBasedOffset))
}
