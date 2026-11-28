package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.utils.FlowVariables
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome

class CelIterationVariableTest {

  private val evaluator = CelLoopConditionEvaluator()

  @Test
  fun `a condition reads the iteration variable as an integer`() {
    val compiled = evaluator.compile(text = "vars[\"iteration\"] <= 3")

    assertEquals(LoopConditionOutcome.True, evaluator.evaluate(compiled = compiled, variables = mapOf(FlowVariables.ITERATION to 3)))
    assertEquals(LoopConditionOutcome.False, evaluator.evaluate(compiled = compiled, variables = mapOf(FlowVariables.ITERATION to 4)))
  }
}
