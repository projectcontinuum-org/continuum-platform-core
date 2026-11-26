package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode

/** Names bound by macros and cel.bind must not shadow an enclosing binding or vars. */
class ReboundNameRuleTest {

  private val validator = CelLoopConditionValidator()

  private fun assertRebound(text: String) {
    val result = validator.check(text = text)
    assertTrue(result is LoopConditionCheckResult.Invalid, "$text must be rejected, got $result")
    assertEquals(LoopConditionIssueCode.REBOUND_NAME, (result as LoopConditionCheckResult.Invalid).code, result.message)
  }

  private fun assertValid(text: String) {
    val result = validator.check(text = text)
    assertTrue(result is LoopConditionCheckResult.Valid, "$text must be valid, got $result")
  }

  @Test
  fun `a nested cel bind that re-binds the outer name is rejected`() {
    assertRebound(text = "cel.bind(a, 1, cel.bind(a, a + a, a > 0))")
  }

  @Test
  fun `a nested macro that re-uses the outer iteration variable is rejected`() {
    assertRebound(text = "vars[\"a\"].exists(x, vars[\"b\"].exists(x, x > 0))")
  }

  @Test
  fun `binding vars is rejected for cel bind and macros`() {
    assertRebound(text = "cel.bind(vars, 1, true)")
    assertRebound(text = "[1].all(vars, true)")
  }

  @Test
  fun `a two-variable macro inside a binding of the same name is rejected`() {
    assertRebound(text = "cel.bind(v, 1, vars.all(k, v, v > 0))")
  }

  @Test
  fun `nested macros with different names are valid`() {
    assertValid(text = "vars[\"a\"].exists(x, vars[\"b\"].exists(y, x == y))")
  }

  @Test
  fun `sibling macros may re-use a name`() {
    assertValid(text = "vars[\"a\"].all(x, x > 0) && vars[\"b\"].all(x, x > 0)")
  }

  @Test
  fun `the range of a nested macro may use the outer name`() {
    assertValid(text = "cel.bind(a, [1, 2], a.exists(b, b > 1))")
  }

  @Test
  fun `the rejection carries the column of the inner binding`() {
    val invalid = validator.check(text = "cel.bind(a, 1, cel.bind(a, a + a, a > 0))") as LoopConditionCheckResult.Invalid

    assertTrue(invalid.column != null && invalid.column!! > 1, "column ${invalid.column}")
  }
}
