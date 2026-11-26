package org.projectcontinuum.core.orchestration.workflow.loop

import io.temporal.workflow.Workflow
import org.projectcontinuum.core.commons.validation.loop.CompiledLoopCondition
import org.projectcontinuum.core.commons.validation.loop.LoopConditionEvaluator
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome

/**
 * Records a condition outcome in the run history, so a replay reads the recorded outcome instead of evaluating again.
 * A cel-java upgrade or a newer time-zone database on another orchestrator can then never change a replayed run.
 * Production: [TEMPORAL], `Workflow.sideEffect`; tests of the loop logic pass a recorder that just runs the block.
 */
fun interface ConditionOutcomeRecorder {
  fun record(evaluate: () -> RecordedConditionOutcome): RecordedConditionOutcome

  companion object {
    val TEMPORAL = ConditionOutcomeRecorder { evaluate -> Workflow.sideEffect(RecordedConditionOutcome::class.java) { evaluate() } }
    val DIRECT = ConditionOutcomeRecorder { evaluate -> evaluate() }
  }
}

/**
 * A [LoopConditionOutcome] in a shape the Temporal data converter writes to and reads from history (a plain class with
 * a no-argument constructor, no polymorphic type information).
 */
class RecordedConditionOutcome(
  var kind: String = "",
  var typeName: String? = null,
  var preview: String? = null,
  var code: String? = null,
  var message: String? = null,
  var variableName: String? = null,
  var column: Int? = null
) {

  fun toOutcome(): LoopConditionOutcome = when (kind) {
    TRUE -> LoopConditionOutcome.True
    FALSE -> LoopConditionOutcome.False
    NON_BOOLEAN -> LoopConditionOutcome.NonBoolean(typeName = typeName.orEmpty(), preview = preview.orEmpty())
    else -> LoopConditionOutcome.Failed(
      code = code?.let { name -> LoopConditionIssueCode.entries.firstOrNull { entry -> entry.name == name } }
        ?: LoopConditionIssueCode.EVALUATION_ERROR,
      message = message.orEmpty(),
      variableName = variableName,
      column = column
    )
  }

  companion object {
    private const val TRUE = "TRUE"
    private const val FALSE = "FALSE"
    private const val NON_BOOLEAN = "NON_BOOLEAN"
    private const val FAILED = "FAILED"

    fun of(outcome: LoopConditionOutcome): RecordedConditionOutcome = when (outcome) {
      LoopConditionOutcome.True -> RecordedConditionOutcome(kind = TRUE)
      LoopConditionOutcome.False -> RecordedConditionOutcome(kind = FALSE)
      is LoopConditionOutcome.NonBoolean -> RecordedConditionOutcome(kind = NON_BOOLEAN, typeName = outcome.typeName, preview = outcome.preview)
      is LoopConditionOutcome.Failed -> RecordedConditionOutcome(
        kind = FAILED,
        code = outcome.code.name,
        message = outcome.message,
        variableName = outcome.variableName,
        column = outcome.column
      )
    }
  }
}

/**
 * One condition check: evaluates [compiled] against the end-of-iteration [variables] inside the [recorder], so the
 * outcome (failures and their messages included) is part of the run history. Inside the recorded block the evaluation
 * goes through the [runner] (production: [TimeBoxedConditionEvaluationRunner], which never lets it block the workflow
 * thread for longer than the time budget plus a small margin). The evaluator never throws; an unexpected exception is
 * still turned into a failed check rather than a failed workflow task.
 */
class LoopConditionCheck(
  private val recorder: ConditionOutcomeRecorder,
  private val runner: () -> ConditionEvaluationRunner = { LoopConditionEvaluators.runner }
) {

  fun evaluate(
    evaluator: LoopConditionEvaluator,
    compiled: CompiledLoopCondition,
    variables: Map<String, Any?>,
    check: ConditionCheckLabel
  ): LoopConditionOutcome {
    val recorded = recorder.record {
      val outcome = runner().run(check = check) {
        evaluateNeverThrowing(evaluator = evaluator, compiled = compiled, variables = variables)
      }
      RecordedConditionOutcome.of(outcome = outcome)
    }
    return recorded.toOutcome()
  }

  private fun evaluateNeverThrowing(
    evaluator: LoopConditionEvaluator,
    compiled: CompiledLoopCondition,
    variables: Map<String, Any?>
  ): LoopConditionOutcome =
    try {
      evaluator.evaluate(compiled = compiled, variables = variables)
    } catch (unexpected: RuntimeException) {
      LoopConditionOutcome.Failed(
        code = LoopConditionIssueCode.EVALUATION_ERROR,
        message = "${LoopConditionIssueCode.EVALUATION_ERROR.reasonText}: ${unexpected.message ?: unexpected.javaClass.simpleName}"
      )
    }
}
