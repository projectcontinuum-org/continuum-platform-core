package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.validation.loop.LoopConditionEvaluator

/**
 * The process-wide [LoopConditionEvaluator] and the [ConditionEvaluationRunner] that runs its evaluations. Temporal
 * instantiates the workflow class itself, so workflow code reaches both statically. The orchestrator installs the real
 * engine and a [TimeBoxedConditionEvaluationRunner] once at start; tests install their own and call [reset] afterwards.
 */
object LoopConditionEvaluators {

  @Volatile
  private var installed: LoopConditionEvaluator? = null

  @Volatile
  private var installedRunner: ConditionEvaluationRunner = ConditionEvaluationRunner.DIRECT

  /** The installed evaluator. Fails with [IllegalStateException] when none is installed (a wiring bug). */
  val current: LoopConditionEvaluator
    get() = installed ?: error("No LoopConditionEvaluator is installed; the orchestrator installs one at start")

  /** The installed runner; [ConditionEvaluationRunner.DIRECT] when only an evaluator was installed (tests). */
  val runner: ConditionEvaluationRunner
    get() = installedRunner

  fun install(evaluator: LoopConditionEvaluator, runner: ConditionEvaluationRunner = ConditionEvaluationRunner.DIRECT) {
    installed = evaluator
    installedRunner = runner
  }

  /** Removes the installed evaluator and runner (tests). */
  fun reset() {
    installed = null
    installedRunner = ConditionEvaluationRunner.DIRECT
  }
}
