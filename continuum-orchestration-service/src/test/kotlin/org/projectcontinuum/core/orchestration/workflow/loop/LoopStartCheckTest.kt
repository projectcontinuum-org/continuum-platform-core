package org.projectcontinuum.core.orchestration.workflow.loop

import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionResultType
import org.projectcontinuum.core.loopcondition.LoopConditionValidators
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The re-check of a loop's settings and condition when it starts. */
class LoopStartCheckTest {

  private val check = LoopStartCheck(validator = LoopConditionValidators.default)

  private fun rejection(overrides: Map<String, Any?>): String {
    val result = check.check(loopId = "L", properties = LoopNodeDescriptor.defaultProperties + overrides)
    assertTrue(result is LoopStartCheck.Result.Rejected, "expected a rejection for $overrides, got $result")
    return (result as LoopStartCheck.Result.Rejected).message
  }

  @Test
  fun `the defaults are accepted with their meaning`() {
    val result = check.check(loopId = "L", properties = LoopNodeDescriptor.defaultProperties) as LoopStartCheck.Result.Accepted

    assertEquals(
      LoopSettings(
        condition = "vars[\"hasMore\"] == true",
        maxIterations = 50,
        onLimit = LimitBehaviour.FAIL,
        onNonBooleanResult = NonBooleanResultStrategy.Fail,
        check = ConditionCheckTiming.AFTER
      ),
      result.settings
    )
  }

  @Test
  fun `maxIterations from Temporal's JSON is accepted as long or whole double`() {
    listOf(7L, 7.0).forEach { value ->
      val result = check.check(loopId = "L", properties = LoopNodeDescriptor.defaultProperties + ("maxIterations" to value))
      assertEquals(7, (result as LoopStartCheck.Result.Accepted).settings.maxIterations)
    }
  }

  @Test
  fun `every bad setting is named in one message`() {
    val message = rejection(overrides = mapOf("maxIterations" to 0, "onLimit" to "ignore", "onNonBooleanResult" to "maybe", "check" to "during"))

    assertTrue(message.startsWith("Loop 'L' cannot start: "), message)
    listOf("maxIterations", "onLimit 'ignore'", "onNonBooleanResult 'maybe'", "check 'during'").forEach { part ->
      assertTrue(message.contains(part), "'$part' missing in $message")
    }
  }

  @Test
  fun `an invalid condition is rejected with the validator's message`() {
    val message = rejection(overrides = mapOf("condition" to "now() > 1"))

    assertTrue(message.contains("the condition does not type-check"), message)
  }

  @Test
  fun `a missing condition is rejected`() {
    assertTrue(rejection(overrides = mapOf("condition" to null)).contains("condition is missing"))
  }

  @Test
  fun `the injected validator decides about the condition`() {
    val alwaysInvalid = LoopStartCheck(validator = {
      LoopConditionCheckResult.Invalid(code = LoopConditionIssueCode.SYNTAX, message = "is not a valid CEL expression")
    })
    val alwaysValid = LoopStartCheck(validator = {
      LoopConditionCheckResult.Valid(resultType = LoopConditionResultType.BOOL, readVariables = emptySet())
    })

    assertTrue(alwaysInvalid.check(loopId = "L", properties = LoopNodeDescriptor.defaultProperties) is LoopStartCheck.Result.Rejected)
    assertTrue(
      alwaysValid.check(loopId = "L", properties = LoopNodeDescriptor.defaultProperties + ("condition" to "whatever")) is LoopStartCheck.Result.Accepted
    )
  }
}
