package org.projectcontinuum.core.loopcondition

import dev.cel.common.values.NullValue
import java.math.BigDecimal
import java.math.BigInteger
import java.util.TreeMap

/**
 * Converts flow variables (JSON values after Temporal's JSON round trip) to the values CEL evaluates: whole numbers
 * (`Integer`, `Long`, `Short`, `Byte`, a `BigInteger` within int64) to `Long` (CEL `int`), other numbers to `Double`
 * (CEL `double`), `Boolean`, `String`, lists, maps with string keys, and `null` to CEL `null`. Any other value is passed
 * as its text.
 *
 * Every map, `vars` itself included, is key-sorted, so iteration order (`vars.map(k, k)`) is the same in every run and
 * on every orchestrator. Nested values are converted without recursion.
 */
internal object FlowVariableCelValues {

  /** The `vars` map for [variables]. */
  fun toCelVariables(variables: Map<String, Any?>): Map<String, Any> {
    @Suppress("UNCHECKED_CAST")
    return toCelValue(value = variables) as Map<String, Any>
  }

  fun toCelValue(value: Any?): Any {
    val root = arrayOfNulls<Any>(1)
    val pending = ArrayDeque<Pair<Any?, (Any) -> Unit>>()
    pending.addLast(value to { converted -> root[0] = converted })
    while (pending.isNotEmpty()) {
      val (source, store) = pending.removeLast()
      when (source) {
        is Map<*, *> -> {
          val target = TreeMap<String, Any>()
          store(target)
          source.forEach { (key, entryValue) ->
            val textKey = key.toString()
            pending.addLast(entryValue to { converted -> target[textKey] = converted })
          }
        }
        is List<*> -> {
          val target = ArrayList<Any>(source.size)
          repeat(times = source.size) { target.add(NullValue.NULL_VALUE) }
          store(target)
          source.forEachIndexed { index, element ->
            pending.addLast(element to { converted -> target[index] = converted })
          }
        }
        else -> store(scalarOf(value = source))
      }
    }
    return root[0] ?: NullValue.NULL_VALUE
  }

  private fun scalarOf(value: Any?): Any = when (value) {
    null -> NullValue.NULL_VALUE
    is Boolean, is String -> value
    is Long -> value
    is Int -> value.toLong()
    is Short -> value.toLong()
    is Byte -> value.toLong()
    is BigInteger -> if (value.bitLength() < Long.SIZE_BITS) value.toLong() else value.toDouble()
    is BigDecimal -> wholeOrDouble(decimal = value)
    is Number -> value.toDouble()
    else -> value.toString()
  }

  /** A `BigDecimal` written without a fraction (`5`) that fits int64 is an int; `5.0` and every other one is a double. */
  private fun wholeOrDouble(decimal: BigDecimal): Any {
    if (decimal.scale() > 0) return decimal.toDouble()
    return runCatching { decimal.longValueExact() }.getOrElse { decimal.toDouble() }
  }
}
