package org.projectcontinuum.core.loopcondition

import dev.cel.common.values.NullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger

/** JSON flow variables become CEL values: int64, double, bool, string, list, key-sorted map, null. */
class FlowVariableCelValuesTest {

  @Test
  fun `numbers become int64 or double`() {
    val converted = FlowVariableCelValues.toCelVariables(
      variables = mapOf(
        "int" to 5,
        "long" to 6L,
        "big" to BigInteger.valueOf(7),
        "wholeDecimal" to BigDecimal("8"),
        "fraction" to BigDecimal("8.5"),
        "float" to 1.5f,
        "double" to 2.5
      )
    )

    assertEquals(5L, converted["int"])
    assertEquals(6L, converted["long"])
    assertEquals(7L, converted["big"])
    assertEquals(8L, converted["wholeDecimal"])
    assertEquals(8.5, converted["fraction"])
    assertEquals(1.5, converted["float"])
    assertEquals(2.5, converted["double"])
  }

  @Test
  fun `null becomes the CEL null value and booleans and strings stay`() {
    val converted = FlowVariableCelValues.toCelVariables(variables = mapOf("n" to null, "b" to true, "s" to "x"))

    assertEquals(NullValue.NULL_VALUE, converted["n"])
    assertEquals(true, converted["b"])
    assertEquals("x", converted["s"])
  }

  @Test
  fun `nested lists and maps are converted and every map is key-sorted`() {
    val converted = FlowVariableCelValues.toCelVariables(
      variables = linkedMapOf("z" to listOf(1, mapOf("b" to 2, "a" to null)), "a" to 1)
    )

    assertEquals(listOf("a", "z"), converted.keys.toList())
    val list = converted["z"] as List<*>
    assertEquals(1L, list[0])
    val nested = list[1] as Map<*, *>
    assertEquals(listOf("a", "b"), nested.keys.toList())
    assertEquals(2L, nested["b"])
    assertEquals(NullValue.NULL_VALUE, nested["a"])
  }

  @Test
  fun `deep nesting is converted without recursion`() {
    var deep: Any = 1
    repeat(times = 10_000) { deep = listOf(deep) }

    val converted = FlowVariableCelValues.toCelValue(value = deep)

    var depth = 0
    var current: Any? = converted
    while (current is List<*>) {
      current = current[0]
      depth += 1
    }
    assertEquals(10_000, depth)
    assertEquals(1L, current)
  }
}
