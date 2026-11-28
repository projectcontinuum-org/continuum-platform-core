package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode

/** The order of the validation steps (the first problem wins) and the message format. */
class CelLoopConditionValidatorTest {

  private val validator = CelLoopConditionValidator()

  private fun codeOf(text: String): LoopConditionIssueCode {
    val result = validator.check(text = text)
    assertTrue(result is LoopConditionCheckResult.Invalid, "'${text.take(40)}' must be invalid, got $result")
    return (result as LoopConditionCheckResult.Invalid).code
  }

  @Test
  fun `a text that is too long is reported as too long even when it is also a syntax error`() {
    assertEquals(LoopConditionIssueCode.TOO_LONG, codeOf(text = "vars[\"a\"] == " + "(".repeat(2040)))
  }

  @Test
  fun `deep nesting is reported before the parser runs`() {
    assertEquals(LoopConditionIssueCode.TOO_DEEP, codeOf(text = "(".repeat(40) + "true" + ")".repeat(40)))
  }

  @Test
  fun `a type error is reported before the result type`() {
    assertEquals(LoopConditionIssueCode.CHECK_ERROR, codeOf(text = "foo + 1"))
  }

  @Test
  fun `the result type is checked before literals and re-bound names`() {
    assertEquals(LoopConditionIssueCode.NOT_BOOLEAN, codeOf(text = "cel.bind(a, 1, cel.bind(a, 2, a)) + size(\"(\".matches(\"(\") ? \"x\" : \"y\")"))
  }

  @Test
  fun `a bad literal is reported before a re-bound name`() {
    assertEquals(LoopConditionIssueCode.INVALID_LITERAL, codeOf(text = "cel.bind(a, 1, cel.bind(a, 2, \"x\".matches(\"(\")))"))
  }

  @Test
  fun `the message starts with the reason text, then the CEL message and the column`() {
    val invalid = validator.check(text = "now() > 1") as LoopConditionCheckResult.Invalid

    assertEquals(4, invalid.column)
    assertEquals(
      "${LoopConditionIssueCode.CHECK_ERROR.reasonText}: undeclared reference to 'now' (in container '') (column 4)",
      invalid.message
    )
  }

  @Test
  fun `multi-line cel-java messages are written on one line`() {
    val invalid = validator.check(text = "vars[\"s\"].matches(\"(\")") as LoopConditionCheckResult.Invalid

    assertTrue(!invalid.message.contains('\n'), invalid.message)
  }

  @Test
  fun `the shared default is the cel-java validator`() {
    assertTrue(LoopConditionValidators.default is CelLoopConditionValidator)
  }
}
