package org.projectcontinuum.core.commons.validation.loop

/**
 * Cost caps of a loop condition. The orchestrator's workflow thread waits for every condition check, at most about
 * 250 ms (see [MAX_EVALUATION_MILLIS]), and Temporal's deadlock detector stops a workflow thread that is blocked for
 * about one second; the caps keep each value, and so most checks, far below that, and keep memory small.
 * Code constants, not configuration: workflow code cannot read deployment settings deterministically.
 *
 * Lengths are counted in Unicode code points, like JSON Schema `maxLength`.
 */
object LoopConditionLimits {
  /** Longest accepted condition text (the properties schema has the same `maxLength`). */
  const val MAX_EXPRESSION_LENGTH = 2048

  /**
   * Deepest accepted nesting of `(`, `[` and `{` outside string literals and comments. Checked on the text before the
   * parser runs, so no parser recurses without bound.
   */
  const val MAX_NESTING_DEPTH = 32

  /** Recursion limit of the CEL parser itself: a long `1+1+1...` chain is a syntax error, not a stack overflow. */
  const val MAX_PARSE_RECURSION_DEPTH = 100

  /** Iterations of all comprehensions (`all`, `exists`, `map`, `filter`, `cel.bind`, ...) in one evaluation together. */
  const val MAX_COMPREHENSION_ITERATIONS = 1000

  /** Most elements of any list or map value produced while a condition is evaluated. */
  const val MAX_COLLECTION_SIZE = 10_000

  /** Most characters of any string value produced while a condition is evaluated. */
  const val MAX_STRING_LENGTH = 100_000

  /** Most bytes of any bytes value produced while a condition is evaluated. */
  const val MAX_BYTES_LENGTH = 100_000

  /** Largest compiled regular expression (re2j program size) for `matches` and the regex functions. */
  const val MAX_REGEX_PROGRAM_SIZE = 1000

  /**
   * Longest time one condition check may take, in milliseconds. The caps above bound each value, but not how often a
   * macro repeats an expensive function (`lists.range(20).all(i, sets.contains(big, big))` is valid and would take
   * seconds), nor how long one function call over two large values runs (`sets.equivalent` over two 9,000-element
   * lists takes about a second).
   *
   * Two mechanisms enforce it:
   * - the evaluator checks the clock after every value it produces and stops there; this cannot interrupt a function
   *   call that is already running;
   * - the orchestrator runs the evaluation on a separate thread and waits for it at most this budget plus a small
   *   margin (250 ms in total). When the wait runs out, the check fails with LIMIT_EXCEEDED, whatever the evaluation
   *   is still doing.
   *
   * So the workflow thread waits at most about 250 ms per check, far below Temporal's one-second deadlock threshold.
   * The outcome is recorded in the run history, so a check that ran out of time stays failed on replay.
   */
  const val MAX_EVALUATION_MILLIS = 200L

  /** Longest preview of a value quoted in a message, in characters. */
  const val VALUE_PREVIEW_LENGTH = 100

  /**
   * The detail of the LIMIT_EXCEEDED failure of a check that ran past [budgetMillis]. The evaluator's own budget and
   * the orchestrator's bounded wait both report it, so the message does not depend on which of them stopped the check.
   */
  fun evaluationTimeExceededDetail(budgetMillis: Long = MAX_EVALUATION_MILLIS): String =
    "the condition took longer than $budgetMillis ms to evaluate"
}
