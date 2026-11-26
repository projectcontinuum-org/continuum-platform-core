package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome

/**
 * Every `evaluations` entry of the CEL condition table against the real evaluator: CEL semantics (a missing variable
 * fails unless the result does not depend on it, `"s" == 1` is false, `"s" > 1` fails, int and double compare by
 * value) and the cost limits. `onNonBooleanResult` is the loop's concern, so a non-boolean result is the same outcome
 * under both settings here.
 */
class CelLoopConditionEvaluatorTableTest {

  private val evaluator = CelLoopConditionEvaluator()

  @TestFactory
  fun `every evaluation of the table gives its expected outcome`(): List<DynamicTest> =
    CelConditionTable.rows.filter { row -> row.valid }.flatMap { row ->
      val compiled = evaluator.compile(text = row.input)
      row.evaluations.mapIndexed { index, evaluation ->
        DynamicTest.dynamicTest("${row.id} #${index + 1} ${evaluation.variables}") {
          val outcome = evaluator.evaluate(compiled = compiled, variables = evaluation.variables)
          assertOutcome(expected = evaluation.expect, actual = outcome, label = row.id)
          evaluation.messageContains?.let { expectedText ->
            assertTrue(messageOf(outcome = outcome).contains(expectedText), "${row.id}: '$expectedText' not in ${messageOf(outcome = outcome)}")
          }
        }
      }
    }

  private fun assertOutcome(expected: String, actual: LoopConditionOutcome, label: String) {
    when (expected) {
      "true" -> assertEquals(LoopConditionOutcome.True, actual, label)
      "false" -> assertEquals(LoopConditionOutcome.False, actual, label)
      "non-boolean" -> assertTrue(actual is LoopConditionOutcome.NonBoolean, "$label: $actual")
      else -> {
        assertTrue(actual is LoopConditionOutcome.Failed, "$label: expected $expected, got $actual")
        val failed = actual as LoopConditionOutcome.Failed
        assertEquals(expected, failed.code.name, "$label: ${failed.message}")
        assertTrue(failed.message.startsWith(failed.code.reasonText), failed.message)
      }
    }
  }

  private fun messageOf(outcome: LoopConditionOutcome): String = when (outcome) {
    is LoopConditionOutcome.Failed -> outcome.message
    is LoopConditionOutcome.NonBoolean -> "${outcome.typeName} ${outcome.preview}"
    else -> outcome.toString()
  }
}
