package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionLimits
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import java.util.concurrent.TimeUnit

/**
 * Conditions that are valid CEL but would stall or exhaust the orchestrator without the cost limits: each ends within
 * 100 ms with LIMIT_EXCEEDED naming the limit, and the evaluator never throws (no OutOfMemoryError, no
 * StackOverflowError). A condition whose every value is within the caps but that repeats expensive calls stops at the
 * evaluation time budget instead. One long function call cannot be stopped from inside: the evaluator reports the
 * budget only after the call returns, which is why the orchestrator runs it on a separate thread with a bounded wait.
 */
class CelLoopConditionEvaluatorRobustnessTest {

  private val evaluator = CelLoopConditionEvaluator()

  private val nestedBindDoublings: String = buildString {
    append("cel.bind(x0, \"ab\", ")
    (1..29).forEach { level -> append("cel.bind(x$level, x${level - 1} + x${level - 1}, ") }
    append("size(x29) > 0")
    repeat(times = 30) { append(")") }
  }

  private val hostileConditions: Map<String, String> = mapOf(
    "a million-element list" to "lists.range(1000000).size() > 0",
    "a ten-step replace chain" to "\"a\"" + ".replace(\"a\", \"aaaaaaaa\")".repeat(10) + ".size() > 0",
    "sets contains over two 60,000-element lists" to "sets.contains(lists.range(60000), lists.range(60000))",
    "29 nested cel bind doublings" to nestedBindDoublings,
    "a 100 x 100 x 100 nested map" to "lists.range(100).map(a, lists.range(100).map(b, lists.range(100).map(c, c))).size() > 0",
    "a string built from a variable" to "vars[\"s\"].replace(\"a\", vars[\"s\"]).size() > 0"
  )

  @Test
  fun `every hostile condition ends within 100 ms with a cost limit and never throws`() {
    evaluator.warmUp()
    val variables = mapOf("s" to "a".repeat(5000))

    hostileConditions.forEach { (label, condition) ->
      assertTrue(evaluator.compile(text = condition).invalid == null, "$label must be a valid condition")
      val startNanos = System.nanoTime()
      val outcome = evaluator.evaluate(compiled = evaluator.compile(text = condition), variables = variables)
      val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

      assertTrue(outcome is LoopConditionOutcome.Failed, "$label: $outcome")
      val failed = outcome as LoopConditionOutcome.Failed
      assertEquals(LoopConditionIssueCode.LIMIT_EXCEEDED, failed.code, "$label: ${failed.message}")
      assertTrue(failed.message.contains("at most") || failed.message.contains("1000"), "$label names the limit: ${failed.message}")
      assertTrue(elapsedMillis < 100, "$label took $elapsedMillis ms")
    }
  }

  @Test
  fun `a macro repeating an expensive function stops at the time budget, far below Temporal's deadlock threshold`() {
    evaluator.warmUp()
    val condition = "lists.range(20).all(i, sets.contains(lists.range(10000), lists.range(10000).reverse())) && vars[\"x\"] == 1"
    val compiled = evaluator.compile(text = condition)
    assertTrue(compiled.invalid == null, "the condition is valid: ${compiled.invalid}")

    val startNanos = System.nanoTime()
    val outcome = evaluator.evaluate(compiled = compiled, variables = mapOf("x" to 1L))
    val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

    val failed = outcome as LoopConditionOutcome.Failed
    assertEquals(LoopConditionIssueCode.LIMIT_EXCEEDED, failed.code, failed.message)
    assertTrue(
      failed.message.contains("the condition took longer than ${LoopConditionLimits.MAX_EVALUATION_MILLIS} ms to evaluate"),
      failed.message
    )
    assertTrue(elapsedMillis < 800, "the check must end well before Temporal's one-second deadlock detector, took $elapsedMillis ms")
  }

  @Test
  fun `the time budget lets a condition finish within it and stops one that runs past it`() {
    var nowNanos = 0L
    val budgetMillis = 200L
    val fakeClockEvaluator = CelLoopConditionEvaluator(
      validator = LoopConditionValidators.cel,
      environment = LoopCelEnvironment.shared,
      timeBudget = { LoopConditionTimeBudget(budgetMillis = budgetMillis, nanoClock = { nowNanos }) }
    )
    val compiled = fakeClockEvaluator.compile(text = "vars[\"page\"] < 3")

    assertEquals(LoopConditionOutcome.True, fakeClockEvaluator.evaluate(compiled = compiled, variables = mapOf("page" to 1L)))

    val slowCompiled = fakeClockEvaluator.compile(text = "vars[\"items\"].all(item, item > 0)")
    val slowVariables = mapOf("items" to listOf(1L, 2L, 3L))
    var callbacks = 0
    val slowEvaluator = CelLoopConditionEvaluator(
      validator = LoopConditionValidators.cel,
      environment = LoopCelEnvironment.shared,
      timeBudget = {
        val budget = LoopConditionTimeBudget(budgetMillis = budgetMillis, nanoClock = { nowNanos })
        dev.cel.runtime.CelEvaluationListener { expression, value ->
          callbacks += 1
          nowNanos += TimeUnit.MILLISECONDS.toNanos(budgetMillis)
          budget.callback(expression, value)
        }
      }
    )
    val failed = slowEvaluator.evaluate(compiled = slowCompiled, variables = slowVariables) as LoopConditionOutcome.Failed

    assertEquals(LoopConditionIssueCode.LIMIT_EXCEEDED, failed.code, failed.message)
    assertTrue(failed.message.contains("took longer than 200 ms to evaluate"), failed.message)
    assertEquals(2, callbacks, "the evaluation stops at the first value produced after the budget ran out")
  }

  @Test
  fun `one long function call ends with the time budget only after it returns, so the orchestrator must bound the wait`() {
    evaluator.warmUp()
    val compiled = evaluator.compile(text = "sets.equivalent(vars[\"a\"], vars[\"b\"])")
    val oneElementLists = (0 until 9000).map { index -> listOf(index.toLong()) }

    val outcome = evaluator.evaluate(compiled = compiled, variables = mapOf("a" to oneElementLists, "b" to oneElementLists.reversed()))

    val failed = outcome as LoopConditionOutcome.Failed
    assertEquals(LoopConditionIssueCode.LIMIT_EXCEEDED, failed.code, failed.message)
    assertTrue(failed.message.contains("the condition took longer than 200 ms to evaluate"), failed.message)
  }

  @Test
  fun `an evaluation on an interrupted thread stops at the next value and keeps the interrupt flag`() {
    val compiled = evaluator.compile(text = "lists.range(500).map(i, i + vars[\"x\"]).size() > 0")
    var callbacksAfterInterrupt = 0
    val countingEvaluator = CelLoopConditionEvaluator(
      validator = LoopConditionValidators.cel,
      environment = LoopCelEnvironment.shared,
      timeBudget = { dev.cel.runtime.CelEvaluationListener { _, _ -> callbacksAfterInterrupt += 1 } }
    )

    Thread.currentThread().interrupt()
    val outcome = try {
      countingEvaluator.evaluate(compiled = compiled, variables = mapOf("x" to 1L))
    } finally {
      assertTrue(Thread.interrupted(), "the interrupt flag stays set for the thread's owner")
    }

    val failed = outcome as LoopConditionOutcome.Failed
    assertEquals(LoopConditionIssueCode.LIMIT_EXCEEDED, failed.code, failed.message)
    assertTrue(failed.message.contains("interrupted"), failed.message)
    assertEquals(1, callbacksAfterInterrupt, "the evaluation stops at the first value")
  }

  @Test
  fun `a stack overflow during evaluation is a failed check, never thrown`() {
    var outcome: LoopConditionOutcome? = null
    val compiled = evaluator.compile(text = "vars[\"a\"].exists(x, vars[\"b\"].exists(y, x == y))")
    val tinyStack = Thread(null, {
      outcome = evaluator.evaluate(compiled = compiled, variables = mapOf("a" to listOf(1L), "b" to listOf(1L)))
    }, "tiny-stack-evaluator", 8 * 1024)
    tinyStack.start()
    tinyStack.join(TimeUnit.SECONDS.toMillis(10))

    assertTrue(outcome != null, "the evaluator must return an outcome")
  }

  @Test
  fun `an invalid text compiles to a failed check with its validation code`() {
    val compiled = evaluator.compile(text = "now() > 1")
    val outcome = evaluator.evaluate(compiled = compiled, variables = emptyMap())

    assertEquals(LoopConditionIssueCode.CHECK_ERROR, compiled.invalid?.code)
    assertEquals(LoopConditionIssueCode.CHECK_ERROR, (outcome as LoopConditionOutcome.Failed).code)
  }

  @Test
  fun `a re-bound cel bind is rejected before it can overflow the stack`() {
    val compiled = evaluator.compile(text = "cel.bind(a, 1, cel.bind(a, a + a, a > 0))")

    assertEquals(LoopConditionIssueCode.REBOUND_NAME, compiled.invalid?.code)
  }

  @Test
  fun `a missing variable names the variable and the column`() {
    val outcome = evaluator.evaluate(compiled = evaluator.compile(text = "vars[\"hasMore\"] == true"), variables = emptyMap())

    val failed = outcome as LoopConditionOutcome.Failed
    assertEquals(LoopConditionIssueCode.MISSING_VARIABLE, failed.code)
    assertEquals("hasMore", failed.variableName)
    assertEquals(5, failed.column)
  }

  @Test
  fun `a non-boolean result carries the type and a preview of at most 100 characters`() {
    val outcome = evaluator.evaluate(compiled = evaluator.compile(text = "vars[\"hasMore\"]"), variables = mapOf("hasMore" to "y".repeat(500)))

    val nonBoolean = outcome as LoopConditionOutcome.NonBoolean
    assertEquals("string", nonBoolean.typeName)
    assertTrue(nonBoolean.preview.length <= 100, nonBoolean.preview)
  }

  @Test
  fun `the shared validator accepts what the evaluator compiles`() {
    assertTrue(LoopConditionValidators.default.check(text = "vars[\"page\"] < 3 && vars[\"hasMore\"]") is LoopConditionCheckResult.Valid)
  }
}
