package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome

/**
 * The CEL environment of loops: the options in force and the exact list of functions and macros. The list is pinned so
 * a cel-java upgrade that adds or removes a function fails here first; review the new functions (cost, determinism)
 * before updating the list.
 */
class LoopCelEnvironmentTest {

  private val validator = CelLoopConditionValidator()
  private val evaluator = CelLoopConditionEvaluator()

  private fun evaluate(text: String, variables: Map<String, Any?> = emptyMap()): LoopConditionOutcome =
    evaluator.evaluate(compiled = evaluator.compile(text = text), variables = variables)

  @Test
  fun `integers and doubles compare by value`() {
    assertEquals(LoopConditionOutcome.True, evaluate(text = "vars[\"n\"] < 2.5", variables = mapOf("n" to 2L)))
    assertEquals(LoopConditionOutcome.True, evaluate(text = "vars[\"n\"] == 1.0", variables = mapOf("n" to 1L)))
    assertEquals(LoopConditionOutcome.True, evaluate(text = "1 < 1.5"))
  }

  @Test
  fun `optional syntax and standard macros are on`() {
    assertTrue(validator.check(text = "vars[?\"x\"].orValue(0) < 3") is LoopConditionCheckResult.Valid)
    assertTrue(validator.check(text = "vars.?x.hasValue()") is LoopConditionCheckResult.Valid)
    assertTrue(validator.check(text = "has(vars.x) && [1].exists(i, i > 0)") is LoopConditionCheckResult.Valid)
  }

  @Test
  fun `protos and native types are not enabled and there is no clock`() {
    val names = LoopCelEnvironment.shared.functionNames()

    assertFalse(names.any { name -> name.startsWith("proto.") }, "protos library must not be enabled: $names")
    assertFalse("now" in names)
  }

  @Test
  fun `the validator and the evaluator use the same environment`() {
    assertEquals(LoopCelEnvironment.shared.functionNames(), evaluator.functionNames())
  }

  @Test
  fun `the function and macro names are exactly the pinned list`() {
    assertEquals(PINNED_FUNCTION_NAMES, LoopCelEnvironment.shared.functionNames())
  }

  @Test
  fun `the cost options are in force`() {
    val budget = evaluate(text = "lists.range(100).map(a, lists.range(100).map(b, b)).size() > 0")
    val regex = evaluate(text = "vars[\"s\"].matches(vars[\"re\"])", variables = mapOf("s" to "a", "re" to "(a{100}){100}"))

    assertTrue(budget is LoopConditionOutcome.Failed && budget.message.contains("1000"), "$budget")
    assertTrue(regex is LoopConditionOutcome.Failed && regex.message.contains("regular expression"), "$regex")
  }

  private companion object {
    val PINNED_FUNCTION_NAMES: Set<String> = sortedSetOf(
      "!_", "-_", "@in", "@not_strictly_false", "@sortByAssociatedKeys", "_!=_", "_%_", "_&&_", "_*_", "_+_", "_-_",
      "_/_", "_<=_", "_<_", "_==_", "_>=_", "_>_", "_?._", "_?_:_", "_[?_]", "_[_]", "_||_", "all", "base64.decode",
      "base64.encode", "bind", "bool", "bytes", "cel.@block", "charAt", "contains", "distinct", "double", "duration",
      "dyn", "endsWith", "exists", "existsOne", "exists_one", "filter", "first", "flatten", "format", "getDate",
      "getDayOfMonth", "getDayOfWeek", "getDayOfYear", "getFullYear", "getHours", "getMilliseconds", "getMinutes",
      "getMonth", "getSeconds", "greatest", "has", "hasValue", "indexOf", "int", "join", "last", "lastIndexOf",
      "least", "lists.range", "lowerAscii", "map", "matches", "math.@max", "math.@min", "math.abs", "math.bitAnd",
      "math.bitNot", "math.bitOr", "math.bitShiftLeft", "math.bitShiftRight", "math.bitXor", "math.ceil",
      "math.floor", "math.isFinite", "math.isInf", "math.isNaN", "math.round", "math.sign", "math.sqrt",
      "math.trunc", "optFlatMap", "optMap", "optional.none", "optional.of", "optional.ofNonZeroValue",
      "optional.unwrap", "or", "orValue", "regex.extract", "regex.extractAll", "regex.replace", "replace", "reverse",
      "sets.contains", "sets.equivalent", "sets.intersects", "size", "slice", "sort", "sortBy", "split",
      "startsWith", "string", "strings.quote", "substring", "timestamp", "transformList", "transformMap",
      "transformMapEntry", "trim", "type", "uint", "upperAscii", "value"
    )
  }
}
