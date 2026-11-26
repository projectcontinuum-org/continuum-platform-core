package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode

/** The text caps that run before the CEL parser. */
class LoopConditionTextPrecheckTest {

  @Test
  fun `blank text is empty`() {
    assertEquals(LoopConditionIssueCode.EMPTY, LoopConditionTextPrecheck.check(text = " \n\t")?.code)
  }

  @Test
  fun `2048 code points pass and 2049 are too long with the column of the first extra character`() {
    assertNull(LoopConditionTextPrecheck.check(text = "x".repeat(2048)))
    val tooLong = LoopConditionTextPrecheck.check(text = "x".repeat(2049))

    assertEquals(LoopConditionIssueCode.TOO_LONG, tooLong?.code)
    assertEquals(2049, tooLong?.column)
  }

  @Test
  fun `a surrogate pair counts as one character`() {
    assertNull(LoopConditionTextPrecheck.check(text = "😀".repeat(2048)))
    assertEquals(LoopConditionIssueCode.TOO_LONG, LoopConditionTextPrecheck.check(text = "😀".repeat(2049))?.code)
  }

  @Test
  fun `depth 32 passes and depth 33 is too deep with a 1-based column`() {
    assertNull(LoopConditionTextPrecheck.check(text = "(".repeat(32) + ")".repeat(32)))
    val tooDeep = LoopConditionTextPrecheck.check(text = "[".repeat(33) + "]".repeat(33))

    assertEquals(LoopConditionIssueCode.TOO_DEEP, tooDeep?.code)
    assertEquals(33, tooDeep?.column)
  }

  @Test
  fun `brackets in strings and comments are ignored`() {
    assertNull(LoopConditionTextPrecheck.check(text = "\"" + "(".repeat(40) + "\" // " + "{".repeat(40)))
    assertNull(LoopConditionTextPrecheck.check(text = "r'''" + "[".repeat(40) + "'''"))
  }
}
