package org.projectcontinuum.core.commons.validation.loop

/**
 * Evaluates a loop condition: the seam between the orchestrator's loop runner and the CEL engine. The contract lives
 * here, engine-free; the cel-java implementation lives in `continuum-loop-condition`, and the orchestration service
 * reaches the installed one through its `LoopConditionEvaluators`.
 *
 * Implementations must be fast and must not depend on random values, I/O or shared mutable state that changes
 * results; every cost is bounded by [LoopConditionLimits]. They must be thread-safe: the orchestrator calls [evaluate]
 * on its own evaluation threads, several at once, and stops waiting for a call that runs past the time budget. Such a
 * call has its thread interrupted and should stop soon after. The one exception to determinism is the time budget
 * ([LoopConditionLimits.MAX_EVALUATION_MILLIS]), which reads the clock: the orchestrator therefore records every
 * outcome in the run history, and a replay never evaluates again. [compile] and [evaluate] never throw: every problem
 * is a value ([CompiledLoopCondition.invalid], [LoopConditionOutcome.Failed]).
 */
interface LoopConditionEvaluator {

  /** Checks and compiles [text] once per loop run. An invalid text gives a [CompiledLoopCondition] with [CompiledLoopCondition.invalid] set. */
  fun compile(text: String): CompiledLoopCondition

  /** Evaluates [compiled] against [variables] (the loop's end-of-iteration flow variables, JSON values). */
  fun evaluate(compiled: CompiledLoopCondition, variables: Map<String, Any?>): LoopConditionOutcome

  /** Loads and exercises the engine once, outside workflow code, so first use cannot trip Temporal's deadlock detector. */
  fun warmUp()
}

/**
 * A compiled condition: the original [text], its checked [resultType] and [readVariables] (used to name a missing
 * variable), and the engine's own program [engineHandle]. [invalid] is set instead when the text did not pass the
 * validator; evaluating such a condition gives [LoopConditionOutcome.Failed] with the validation code.
 */
class CompiledLoopCondition(
  val text: String,
  val resultType: LoopConditionResultType?,
  val readVariables: Set<String>,
  val engineHandle: Any?,
  val invalid: LoopConditionCheckResult.Invalid? = null
)

/** The outcome of one condition check. */
sealed interface LoopConditionOutcome {
  /** The condition returned `true`: run the next iteration. */
  data object True : LoopConditionOutcome

  /** The condition returned `false`: the loop ends. */
  data object False : LoopConditionOutcome

  /**
   * A `dyn` condition returned something else. [typeName] is the CEL type (`string`, `int`, `list`, ...), [preview] the
   * value as text, at most [LoopConditionLimits.VALUE_PREVIEW_LENGTH] characters.
   */
  data class NonBoolean(val typeName: String, val preview: String) : LoopConditionOutcome

  /**
   * The check failed: [code] is a runtime [LoopConditionIssueCode] (or a validation code for an invalid text),
   * [message] starts with its reason text, [variableName] names the flow variable for
   * [LoopConditionIssueCode.MISSING_VARIABLE] when it is known, [column] is 1-based when known.
   */
  data class Failed(
    val code: LoopConditionIssueCode,
    val message: String,
    val variableName: String? = null,
    val column: Int? = null
  ) : LoopConditionOutcome
}
