package org.projectcontinuum.core.loopcondition

import dev.cel.common.ast.CelExpr
import dev.cel.common.ast.CelExpr.ExprKind.Kind

/** Iterative walks over cel-java expressions: a deep expression can never overflow the stack here. */
internal object CelAstWalk {

  /** [root] and every expression below it, pre-order and left to right, without recursion. */
  fun subExpressions(root: CelExpr): Sequence<CelExpr> = sequence {
    val pending = ArrayDeque<CelExpr>()
    pending.addLast(root)
    while (pending.isNotEmpty()) {
      val current = pending.removeLast()
      yield(current)
      directChildren(expression = current).asReversed().forEach { child -> pending.addLast(child) }
    }
  }

  /** The direct sub-expressions of [expression], in source order (a comprehension's parts in evaluation order). */
  fun directChildren(expression: CelExpr): List<CelExpr> = when (expression.getKind()) {
    Kind.SELECT -> listOf(expression.select().operand())
    Kind.CALL -> {
      val call = expression.call()
      val target = if (call.target().isPresent) listOf(call.target().get()) else emptyList()
      target + call.args()
    }
    Kind.LIST -> expression.list().elements()
    Kind.MAP -> expression.map().entries().flatMap { entry -> listOf(entry.key(), entry.value()) }
    Kind.STRUCT -> expression.struct().entries().map { entry -> entry.value() }
    Kind.COMPREHENSION -> {
      val comprehension = expression.comprehension()
      listOf(
        comprehension.iterRange(),
        comprehension.accuInit(),
        comprehension.loopCondition(),
        comprehension.loopStep(),
        comprehension.result()
      )
    }
    else -> emptyList()
  }
}
