package org.projectcontinuum.core.orchestration.workflow.loop

import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import kotlin.test.assertEquals

/** Every condition outcome under both onNonBooleanResult settings, with the exact message texts. */
class ConditionOutcomePolicyTest {

  private val iteration2 = ConditionCheckPoint.AfterIteration(iteration = 2)
  private val strategies = listOf(NonBooleanResultStrategy.Fail, NonBooleanResultStrategy.TreatAsFalse)

  private fun decide(outcome: LoopConditionOutcome, strategy: NonBooleanResultStrategy, checkPoint: ConditionCheckPoint = iteration2) =
    ConditionOutcomePolicy.decide(loopId = "L", outcome = outcome, onNonBooleanResult = strategy, checkPoint = checkPoint)

  @Test
  fun `true continues and false stops with success under both settings`() {
    strategies.forEach { strategy ->
      assertEquals(LoopDecision.Continue, decide(outcome = LoopConditionOutcome.True, strategy = strategy))
      assertEquals(LoopDecision.StopSuccess, decide(outcome = LoopConditionOutcome.False, strategy = strategy))
    }
  }

  @Test
  fun `a non-boolean result fails with type, preview and iteration under fail`() {
    val decision = decide(outcome = LoopConditionOutcome.NonBoolean(typeName = "string", preview = "\"yes\""), strategy = NonBooleanResultStrategy.Fail)

    assertEquals(LoopDecision.Fail(message = "Loop 'L': the condition returned a string (\"yes\") in iteration 2, not true or false."), decision)
  }

  @Test
  fun `the article follows the type name`() {
    val decision = decide(outcome = LoopConditionOutcome.NonBoolean(typeName = "int", preview = "3"), strategy = NonBooleanResultStrategy.Fail)

    assertEquals(LoopDecision.Fail(message = "Loop 'L': the condition returned an int (3) in iteration 2, not true or false."), decision)
  }

  @Test
  fun `a non-boolean result stops with a warning under treat-as-false, also before the first iteration`() {
    val nonBoolean = LoopConditionOutcome.NonBoolean(typeName = "string", preview = "\"yes\"")

    assertEquals(
      LoopDecision.StopWithWarning(message = "Condition returned string in iteration 2; treated as false."),
      decide(outcome = nonBoolean, strategy = NonBooleanResultStrategy.TreatAsFalse)
    )
    assertEquals(
      LoopDecision.StopWithWarning(message = "Condition returned string before the first iteration; treated as false."),
      decide(outcome = nonBoolean, strategy = NonBooleanResultStrategy.TreatAsFalse, checkPoint = ConditionCheckPoint.BeforeFirstIteration)
    )
  }

  @Test
  fun `a missing variable names the variable, the iteration and the column`() {
    val failed = LoopConditionOutcome.Failed(
      code = LoopConditionIssueCode.MISSING_VARIABLE,
      message = "reads a flow variable that is not set: 'hasMore' (column 1)",
      variableName = "hasMore",
      column = 1
    )

    strategies.forEach { strategy ->
      assertEquals(
        LoopDecision.Fail(message = "Loop 'L': flow variable 'hasMore' is not set when the condition is checked in iteration 2 (column 1)."),
        decide(outcome = failed, strategy = strategy)
      )
    }
  }

  @Test
  fun `other failures name the code and the CEL message`() {
    val failed = LoopConditionOutcome.Failed(
      code = LoopConditionIssueCode.LIMIT_EXCEEDED,
      message = "exceeded a cost limit: a value has 1000000 elements in a list, at most 10000 are allowed (column 12)"
    )

    assertEquals(
      LoopDecision.Fail(
        message = "Loop 'L': the condition check in iteration 2 failed with LIMIT_EXCEEDED: the condition exceeded a cost limit: " +
          "a value has 1000000 elements in a list, at most 10000 are allowed (column 12)."
      ),
      decide(outcome = failed, strategy = NonBooleanResultStrategy.TreatAsFalse)
    )
  }

  @Test
  fun `each property value selects its strategy`() {
    assertEquals(NonBooleanResultStrategy.Fail, NonBooleanResultStrategy.forPropertyValue(value = "fail"))
    assertEquals(NonBooleanResultStrategy.TreatAsFalse, NonBooleanResultStrategy.forPropertyValue(value = "treat-as-false"))
    assertEquals(null, NonBooleanResultStrategy.forPropertyValue(value = "maybe"))
  }
}
