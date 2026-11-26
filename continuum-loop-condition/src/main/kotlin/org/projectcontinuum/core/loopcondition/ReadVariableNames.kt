package org.projectcontinuum.core.loopcondition

import dev.cel.common.CelAbstractSyntaxTree
import dev.cel.common.Operator
import dev.cel.common.ast.CelConstant
import dev.cel.common.ast.CelExpr
import dev.cel.common.ast.CelExpr.ExprKind.Kind
import org.projectcontinuum.core.commons.validation.loop.LoopConditionVariables

/**
 * The flow variable names a condition reads with a literal key: `vars["name"]`, `vars[?"name"]`, `vars.name`,
 * `has(vars.name)`, `vars.?name` and `"name" in vars`. A key computed at run time (`vars[vars["k"]]`) adds nothing.
 * Used to name a missing variable in a run-time message and to warn about a condition that reads no variable at all.
 * Runs after [ReboundNameRule], so `vars` always means the flow variables here.
 */
internal object ReadVariableNames {

  private val indexFunctions = setOf(Operator.INDEX.function, Operator.OPTIONAL_INDEX.function, Operator.OPTIONAL_SELECT.function)

  fun of(checkedAst: CelAbstractSyntaxTree): Set<String> =
    CelAstWalk.subExpressions(root = checkedAst.expr)
      .mapNotNull { expression -> readName(expression = expression) }
      .toCollection(LinkedHashSet())

  private fun readName(expression: CelExpr): String? = when (expression.getKind()) {
    Kind.SELECT -> {
      val select = expression.select()
      if (isVariables(expression = select.operand())) select.field() else null
    }
    Kind.CALL -> readNameOfCall(call = expression.call())
    else -> null
  }

  private fun readNameOfCall(call: CelExpr.CelCall): String? {
    if (call.target().isPresent || call.args().size != 2) return null
    val (first, second) = call.args()
    return when (call.function()) {
      in indexFunctions -> if (isVariables(expression = first)) stringConstant(expression = second) else null
      Operator.IN.function -> if (isVariables(expression = second)) stringConstant(expression = first) else null
      else -> null
    }
  }

  private fun isVariables(expression: CelExpr): Boolean =
    expression.getKind() == Kind.IDENT && expression.ident().name() == LoopConditionVariables.IDENTIFIER

  private fun stringConstant(expression: CelExpr): String? {
    if (expression.getKind() != Kind.CONSTANT) return null
    val constant = expression.constant()
    return if (constant.getKind() == CelConstant.Kind.STRING_VALUE) constant.stringValue() else null
  }
}
