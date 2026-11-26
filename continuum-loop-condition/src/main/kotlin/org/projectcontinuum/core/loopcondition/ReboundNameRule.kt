package org.projectcontinuum.core.loopcondition

import dev.cel.common.CelAbstractSyntaxTree
import dev.cel.common.ast.CelExpr
import dev.cel.common.ast.CelExpr.ExprKind.Kind
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionVariables

/**
 * Rejects a comprehension variable (`x` in `list.exists(x, ...)`, `k` and `v` of two-variable macros) or a `cel.bind`
 * name that is already bound by an enclosing comprehension, or that is `vars`. cel-java 0.15.0 throws a
 * [StackOverflowError] when it evaluates a `cel.bind` that re-binds an outer name
 * (`cel.bind(a, 1, cel.bind(a, a + a, a > 0))`), and shadowing `vars` would hide the flow variables; nested
 * comprehensions with different names, and the same name in sibling comprehensions, stay valid.
 *
 * Macro expansions give every comprehension an internal accumulator (`@result`, or `__result__` in older ASTs) and
 * `cel.bind` an unused iteration variable (`#unused`); a user can never write those names, so they are ignored. The
 * walk is iterative, with the bound names carried per pending expression.
 */
internal object ReboundNameRule {

  private val internalNames = setOf("__result__")

  private class Pending(val expression: CelExpr, val boundNames: Set<String>)

  fun check(checkedAst: CelAbstractSyntaxTree): LoopConditionCheckResult.Invalid? {
    val positions = checkedAst.source.positionsMap
    val pending = ArrayDeque<Pending>()
    pending.addLast(Pending(expression = checkedAst.expr, boundNames = emptySet()))
    while (pending.isNotEmpty()) {
      val current = pending.removeLast()
      val expression = current.expression
      if (expression.getKind() != Kind.COMPREHENSION) {
        CelAstWalk.directChildren(expression = expression).asReversed().forEach { child ->
          pending.addLast(Pending(expression = child, boundNames = current.boundNames))
        }
        continue
      }
      val comprehension = expression.comprehension()
      val newNames = userBoundNames(comprehension = comprehension)
      val reboundName = newNames.firstOrNull { name ->
        name in current.boundNames || name == LoopConditionVariables.IDENTIFIER
      }
      if (reboundName != null) {
        return LoopConditionIssues.invalid(
          code = LoopConditionIssueCode.REBOUND_NAME,
          detail = describe(name = reboundName),
          zeroBasedOffset = positions[expression.id()]
        )
      }
      val innerScope = current.boundNames + newNames
      // The range and the initial value are evaluated outside the comprehension; the rest sees its variables.
      listOf(comprehension.result(), comprehension.loopStep(), comprehension.loopCondition()).forEach { scoped ->
        pending.addLast(Pending(expression = scoped, boundNames = innerScope))
      }
      listOf(comprehension.accuInit(), comprehension.iterRange()).forEach { outer ->
        pending.addLast(Pending(expression = outer, boundNames = current.boundNames))
      }
    }
    return null
  }

  private fun userBoundNames(comprehension: CelExpr.CelComprehension): List<String> =
    listOf(comprehension.iterVar(), comprehension.iterVar2(), comprehension.accuVar())
      .filter { name -> isUserName(name = name) }
      .distinct()

  /** A name a condition author wrote: a CEL identifier that is not one of the macro expansion's internal names. */
  private fun isUserName(name: String?): Boolean =
    !name.isNullOrEmpty() &&
      name !in internalNames &&
      (name[0] == '_' || name[0].isLetter()) &&
      name.all { character -> character == '_' || character.isLetterOrDigit() }

  private fun describe(name: String): String =
    if (name == LoopConditionVariables.IDENTIFIER) {
      "'$name' is the map of flow variables and cannot be re-bound"
    } else {
      "'$name' is already bound by an enclosing macro or cel.bind; use a different name"
    }
}
