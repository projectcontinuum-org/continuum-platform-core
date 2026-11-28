package org.projectcontinuum.core.loopcondition

import dev.cel.runtime.CelEvaluationException
import dev.cel.runtime.CelEvaluationListener
import dev.cel.runtime.CelRuntime
import org.projectcontinuum.core.commons.validation.loop.CompiledLoopCondition
import org.projectcontinuum.core.commons.validation.loop.LoopConditionEvaluator
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import org.projectcontinuum.core.commons.validation.loop.LoopConditionVariables

/**
 * The cel-java [LoopConditionEvaluator], built on the same [LoopCelEnvironment] as the validator.
 *
 * - [compile] runs the full validator first and keeps the cel-java program; an invalid text is returned as a compiled
 *   condition with `invalid` set, never thrown.
 * - [evaluate] converts the flow variables ([FlowVariableCelValues]), runs the program with `trace` so the
 *   [LoopConditionSizeGuard], a fresh [LoopConditionTimeBudget] and the [EvaluationInterruptCheck] see every
 *   intermediate value, and maps the result: `true`/`false`, any other value of a `dyn` condition as
 *   [LoopConditionOutcome.NonBoolean], every evaluation error, guard abort and [StackOverflowError] as
 *   [LoopConditionOutcome.Failed] ([CelEvaluationFailures]). It never throws.
 *
 * The guards act between values, so one function call that is already running is never cut short. The orchestrator
 * therefore runs [evaluate] on an evaluation thread inside `Workflow.sideEffect` and waits only a bounded time; it
 * records each outcome in the run history, so a replay never evaluates again (which is also why the wall-clock time
 * budget is safe). Stateless and thread-safe: evaluations may run on several threads at once.
 *
 * @param timeBudget Creates the time budget of one evaluation (tests pass one with a fake clock).
 */
class CelLoopConditionEvaluator internal constructor(
  private val validator: CelLoopConditionValidator,
  private val environment: LoopCelEnvironment,
  private val timeBudget: () -> CelEvaluationListener = { LoopConditionTimeBudget() }
) : LoopConditionEvaluator {

  constructor() : this(validator = LoopConditionValidators.cel, environment = LoopCelEnvironment.shared)

  private companion object {
    const val WARM_UP_CONDITION = "vars[\"page\"] < 3 && vars[\"hasMore\"]"
  }

  override fun compile(text: String): CompiledLoopCondition {
    return when (val analysis = validator.analyze(text = text)) {
      is CelLoopConditionValidator.Analysis.Rejected -> CompiledLoopCondition(
        text = text,
        resultType = null,
        readVariables = emptySet(),
        engineHandle = null,
        invalid = analysis.issue
      )
      is CelLoopConditionValidator.Analysis.Accepted -> compileAccepted(text = text, analysis = analysis)
    }
  }

  override fun evaluate(compiled: CompiledLoopCondition, variables: Map<String, Any?>): LoopConditionOutcome {
    compiled.invalid?.let { invalid -> return LoopConditionIssues.failedFrom(invalid = invalid) }
    val program = compiled.engineHandle as? CelRuntime.Program
      ?: return LoopConditionIssues.failed(
        code = LoopConditionIssueCode.EVALUATION_ERROR,
        detail = "the condition was not compiled by the cel-java evaluator"
      )
    return try {
      val celVariables = FlowVariableCelValues.toCelVariables(variables = variables)
      val guards = CombinedEvaluationListener(guards = listOf(LoopConditionSizeGuard, timeBudget(), EvaluationInterruptCheck))
      val result = program.trace(mapOf(LoopConditionVariables.IDENTIFIER to celVariables), guards)
      outcomeOf(result = result)
    } catch (evaluationError: CelEvaluationException) {
      CelEvaluationFailures.failed(exception = evaluationError, readVariables = compiled.readVariables)
    } catch (stackOverflow: StackOverflowError) {
      CelEvaluationFailures.nestedTooDeeply()
    } catch (unexpected: RuntimeException) {
      CelEvaluationFailures.unexpected(exception = unexpected)
    }
  }

  override fun warmUp() {
    val compiled = compile(text = WARM_UP_CONDITION)
    evaluate(compiled = compiled, variables = mapOf("page" to 1L, "hasMore" to true))
  }

  /** The names of every function and macro the evaluator offers (the same list as the validator's environment). */
  fun functionNames(): Set<String> = environment.functionNames()

  private fun compileAccepted(text: String, analysis: CelLoopConditionValidator.Analysis.Accepted): CompiledLoopCondition =
    try {
      CompiledLoopCondition(
        text = text,
        resultType = analysis.resultType,
        readVariables = analysis.readVariables,
        engineHandle = environment.program(checkedAst = analysis.checkedAst)
      )
    } catch (programFailure: Exception) {
      CompiledLoopCondition(
        text = text,
        resultType = analysis.resultType,
        readVariables = analysis.readVariables,
        engineHandle = null,
        invalid = LoopConditionIssues.invalid(
          code = LoopConditionIssueCode.SYNTAX,
          detail = programFailure.message ?: programFailure.javaClass.simpleName
        )
      )
    }

  private fun outcomeOf(result: Any?): LoopConditionOutcome = when (result) {
    true -> LoopConditionOutcome.True
    false -> LoopConditionOutcome.False
    else -> ValuePreview.nonBoolean(value = result)
  }
}
