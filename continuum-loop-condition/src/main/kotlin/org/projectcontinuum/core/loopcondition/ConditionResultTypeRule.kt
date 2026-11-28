package org.projectcontinuum.core.loopcondition

import dev.cel.common.CelAbstractSyntaxTree
import dev.cel.common.types.CelKind
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionResultType

/**
 * A loop condition must end in true or false. The checker knows the result type: `bool` is fine, `dyn` (for example
 * `vars["hasMore"]`) is decided at run time by the loop's `onNonBooleanResult` setting, and anything else
 * (`vars["page"] + 1`, `1 + 1`, `null`, a string) can never be true or false, so it is rejected before a run.
 */
internal object ConditionResultTypeRule {

  sealed interface Verdict {
    data class Accepted(val resultType: LoopConditionResultType) : Verdict
    data class Rejected(val issue: LoopConditionCheckResult.Invalid) : Verdict
  }

  fun check(checkedAst: CelAbstractSyntaxTree): Verdict {
    val resultType = checkedAst.resultType
    return when (resultType.kind()) {
      CelKind.BOOL -> Verdict.Accepted(resultType = LoopConditionResultType.BOOL)
      CelKind.DYN -> Verdict.Accepted(resultType = LoopConditionResultType.DYN)
      else -> Verdict.Rejected(
        issue = LoopConditionIssues.invalid(
          code = LoopConditionIssueCode.NOT_BOOLEAN,
          detail = "its type is ${resultType.name()}",
          zeroBasedOffset = 0
        )
      )
    }
  }
}
