package org.projectcontinuum.core.loopcondition

import dev.cel.common.ast.CelExpr
import dev.cel.common.values.CelByteString
import dev.cel.runtime.CelEvaluationListener
import org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits

/**
 * Caps every intermediate value of one evaluation: lists and maps at [LoopConditionLimits.MAX_COLLECTION_SIZE]
 * elements, strings at [LoopConditionLimits.MAX_STRING_LENGTH] characters, bytes at
 * [LoopConditionLimits.MAX_BYTES_LENGTH]. cel-java meters comprehensions only; functions such as `lists.range`, a
 * chain of `replace`, nested `cel.bind` doublings or the quadratic `sets.*` functions would otherwise allocate without
 * bound or run for seconds. cel-java calls this listener with each value right after it is
 * produced; throwing [SizeLimitExceeded] aborts the evaluation before the next function sees the oversized value.
 */
internal object LoopConditionSizeGuard : CelEvaluationListener {

  /** Thrown from the listener; the evaluator recognises it as the cause of the evaluation error. */
  class SizeLimitExceeded(limitDescription: String) : EvaluationLimitExceeded(limitDescription = limitDescription)

  override fun callback(expression: CelExpr, value: Any?) {
    when (value) {
      is String -> requireAtMost(size = value.length, limit = LoopConditionLimits.MAX_STRING_LENGTH, what = "characters in a string")
      is Collection<*> -> requireAtMost(size = value.size, limit = LoopConditionLimits.MAX_COLLECTION_SIZE, what = "elements in a list")
      is Map<*, *> -> requireAtMost(size = value.size, limit = LoopConditionLimits.MAX_COLLECTION_SIZE, what = "entries in a map")
      is CelByteString -> requireAtMost(size = value.size(), limit = LoopConditionLimits.MAX_BYTES_LENGTH, what = "bytes in a bytes value")
      else -> Unit
    }
  }

  private fun requireAtMost(size: Int, limit: Int, what: String) {
    if (size > limit) {
      throw SizeLimitExceeded(limitDescription = "a value has $size $what, at most $limit are allowed")
    }
  }
}
