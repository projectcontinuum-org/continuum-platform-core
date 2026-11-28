package org.projectcontinuum.core.orchestration.workflow.loop

import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import org.projectcontinuum.core.loopcondition.CelLoopConditionEvaluator
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One condition check with the real evaluator and the production runner: a single function call that runs far past
 * the time budget (`sets.equivalent` over two 9,000-element lists takes about a second on its own) must not hold the
 * calling thread, which in production is the workflow thread, for longer than the budget plus the wait margin.
 */
class LoopConditionCheckTest {

  private val check = ConditionCheckLabel(loopId = "L", checkPoint = ConditionCheckPoint.AfterIteration(iteration = 1))

  @Test
  fun `one slow sets call over two large flow-variable lists fails within the budget and margin`() {
    val evaluator = CelLoopConditionEvaluator()
    evaluator.warmUp()
    val compiled = evaluator.compile(text = "sets.equivalent(vars[\"a\"], vars[\"b\"])")
    val oneElementLists = (0 until 9000).map { index -> listOf(index.toLong()) }
    val variables = mapOf("a" to oneElementLists, "b" to oneElementLists.reversed())
    val warnings = mutableListOf<String>()
    TimeBoxedConditionEvaluationRunner(warn = { message -> warnings += message }).use { runner ->
      val conditionCheck = LoopConditionCheck(recorder = ConditionOutcomeRecorder.DIRECT, runner = { runner })

      val startNanos = System.nanoTime()
      val outcome = conditionCheck.evaluate(evaluator = evaluator, compiled = compiled, variables = variables, check = check)
      val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

      val failed = outcome as LoopConditionOutcome.Failed
      assertEquals(LoopConditionIssueCode.LIMIT_EXCEEDED, failed.code, failed.message)
      assertEquals("exceeded a cost limit: the condition took longer than 200 ms to evaluate", failed.message)
      assertTrue(elapsedMillis < 450, "the check must return within the 250 ms wait plus scheduling slack, took $elapsedMillis ms")
      assertEquals(1, warnings.size, "one warning per abandoned check: $warnings")
    }
  }

  @Test
  fun `a fast condition returns its own outcome through the runner`() {
    val evaluator = CelLoopConditionEvaluator()
    val compiled = evaluator.compile(text = "vars[\"page\"] < 3")
    TimeBoxedConditionEvaluationRunner().use { runner ->
      val conditionCheck = LoopConditionCheck(recorder = ConditionOutcomeRecorder.DIRECT, runner = { runner })

      val outcome = conditionCheck.evaluate(evaluator = evaluator, compiled = compiled, variables = mapOf("page" to 1L), check = check)

      assertEquals(LoopConditionOutcome.True, outcome)
    }
  }
}
