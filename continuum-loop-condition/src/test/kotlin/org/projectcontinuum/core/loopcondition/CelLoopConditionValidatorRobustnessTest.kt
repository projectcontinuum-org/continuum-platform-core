package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import java.util.concurrent.TimeUnit

/** Hostile condition texts at the length limit: the validator answers quickly and never throws. */
class CelLoopConditionValidatorRobustnessTest {

  private val validator = CelLoopConditionValidator()

  @Test
  fun `hostile inputs at the length limit return within 100 ms and never throw`() {
    val hostileInputs = listOf(
      "(".repeat(1023) + ")".repeat(1023),
      "vars[\"a\"] == 1" + "+1".repeat(1015),
      "!".repeat(2040) + "true",
      "-".repeat(2040) + "1",
      "vars" + ".a".repeat(1018) + " == 1",
      "vars[\"a\"] == \"" + "\\\\".repeat(1015) + "\"",
      "[".repeat(32) + "1" + "]".repeat(32) + ".size() > 0" + " ".repeat(1900),
      "true" + " && [1].exists(x, x > 0)".repeat(80),
      "vars[\"a\"] == x" + ".f()".repeat(500)
    )
    validator.check(text = "vars[\"warm\"] == true")

    hostileInputs.forEach { hostileInput ->
      assertTrue(hostileInput.length <= 2048, "input of ${hostileInput.length} characters")
      val startNanos = System.nanoTime()
      val result = validator.check(text = hostileInput)
      val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

      assertTrue(result is LoopConditionCheckResult.Valid || result is LoopConditionCheckResult.Invalid)
      assertTrue(elapsedMillis < 100, "hostile input '${hostileInput.take(30)}...' took $elapsedMillis ms")
    }
  }

  @Test
  fun `depth 32 is accepted and depth 33 is too deep with the column of the 33rd bracket`() {
    val atLimit = validator.check(text = "(".repeat(32) + "true" + ")".repeat(32))
    val overLimit = validator.check(text = "vars[\"a\"] == " + "(".repeat(33) + "1" + ")".repeat(33)) as LoopConditionCheckResult.Invalid

    assertTrue(atLimit is LoopConditionCheckResult.Valid, "$atLimit")
    assertEquals(LoopConditionIssueCode.TOO_DEEP, overLimit.code)
    assertEquals(46, overLimit.column)
  }

  @Test
  fun `brackets inside strings and comments do not count as nesting`() {
    val bracketsInString = "vars[\"" + "(".repeat(40) + "\"] == \"" + "[".repeat(40) + "\""
    val bracketsInComment = "vars[\"a\"] == 1 // " + "{".repeat(40)

    assertTrue(validator.check(text = bracketsInString) is LoopConditionCheckResult.Valid)
    assertTrue(validator.check(text = bracketsInComment) is LoopConditionCheckResult.Valid)
  }

  @Test
  fun `the length cap counts code points, so a surrogate pair is one character`() {
    val prefix = "vars[\"a\"] == \""
    val atLimit = prefix + "😀".repeat(2048 - prefix.length - 1) + "\""
    val overLimit = prefix + "😀".repeat(2048 - prefix.length) + "\""

    assertTrue(validator.check(text = atLimit) is LoopConditionCheckResult.Valid)
    assertEquals(LoopConditionIssueCode.TOO_LONG, (validator.check(text = overLimit) as LoopConditionCheckResult.Invalid).code)
  }

  @Test
  fun `a stack overflow while parsing is caught as a syntax error`() {
    var result: LoopConditionCheckResult? = null
    val deepButShortChain = "1" + "+1".repeat(1000) + " > 0"
    val tinyStack = Thread(null, { result = validator.check(text = deepButShortChain) }, "tiny-stack-validator", 32 * 1024)
    tinyStack.start()
    tinyStack.join(TimeUnit.SECONDS.toMillis(10))

    val outcome = result as? LoopConditionCheckResult.Invalid
    assertTrue(outcome != null, "the validator must return an issue, got $result")
    assertEquals(LoopConditionIssueCode.SYNTAX, outcome!!.code)
  }
}
