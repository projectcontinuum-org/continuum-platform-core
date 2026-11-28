package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult

/** The flow variables a condition reads with a literal key. */
class ReadVariableNamesTest {

  private val validator = CelLoopConditionValidator()

  private fun readsOf(text: String): Set<String> = (validator.check(text = text) as LoopConditionCheckResult.Valid).readVariables

  @Test
  fun `every literal access form is found`() {
    assertEquals(setOf("a"), readsOf(text = "vars[\"a\"] == 1"))
    assertEquals(setOf("b"), readsOf(text = "vars.b == 1"))
    assertEquals(setOf("c"), readsOf(text = "has(vars.c)"))
    assertEquals(setOf("d"), readsOf(text = "\"d\" in vars"))
    assertEquals(setOf("e"), readsOf(text = "vars[?\"e\"].hasValue()"))
    assertEquals(setOf("f"), readsOf(text = "vars.?f.hasValue()"))
  }

  @Test
  fun `dotted names are read through the index form`() {
    assertEquals(setOf("restNode_3.durationMs"), readsOf(text = "vars[\"restNode_3.durationMs\"] > 1000"))
  }

  @Test
  fun `a computed key adds nothing but the key's own variable`() {
    assertEquals(setOf("key"), readsOf(text = "vars[vars[\"key\"]] == 1"))
  }

  @Test
  fun `a condition without vars reads nothing`() {
    assertEquals(emptySet<String>(), readsOf(text = "true"))
    assertEquals(emptySet<String>(), readsOf(text = "vars.all(k, v, k != \"\")"))
  }

  @Test
  fun `names inside macros are found too`() {
    assertEquals(setOf("items", "limit"), readsOf(text = "vars[\"items\"].exists(i, i > vars[\"limit\"])"))
  }
}
