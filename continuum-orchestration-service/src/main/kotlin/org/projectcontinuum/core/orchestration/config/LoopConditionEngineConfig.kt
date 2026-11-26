package org.projectcontinuum.core.orchestration.config

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.projectcontinuum.core.loopcondition.CelLoopConditionEvaluator
import org.projectcontinuum.core.orchestration.workflow.loop.LoopConditionEvaluators
import org.projectcontinuum.core.orchestration.workflow.loop.TimeBoxedConditionEvaluationRunner
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Installs the cel-java loop condition evaluator for workflow code, with the threads that run its evaluations:
 *
 * - at bean start it warms the evaluator up, outside any workflow (the first use of cel-java loads its classes, about
 *   100 ms, which on the workflow thread could trip Temporal's deadlock detector), and starts the evaluation threads
 *   ([TimeBoxedConditionEvaluationRunner]), which keep one long condition from blocking a workflow thread;
 * - at shutdown it stops those threads without waiting, so an evaluation stuck in a long call cannot hold the
 *   shutdown up. A condition check that comes after that fails its workflow task, which Temporal retries.
 */
@Component
class LoopConditionEngineConfig {

  private companion object {
    private val LOGGER = LoggerFactory.getLogger(LoopConditionEngineConfig::class.java)
  }

  private var evaluationRunner: TimeBoxedConditionEvaluationRunner? = null

  @PostConstruct
  fun installEvaluator() {
    val evaluator = CelLoopConditionEvaluator()
    val startNanos = System.nanoTime()
    evaluator.warmUp()
    val runner = TimeBoxedConditionEvaluationRunner()
    evaluationRunner = runner
    LoopConditionEvaluators.install(evaluator = evaluator, runner = runner)
    LOGGER.info(
      "Loop condition evaluator installed and warmed up in {} ms, with {} evaluation threads",
      (System.nanoTime() - startNanos) / 1_000_000,
      TimeBoxedConditionEvaluationRunner.EVALUATION_THREADS
    )
  }

  @PreDestroy
  fun stopEvaluationThreads() {
    evaluationRunner?.close()
  }
}
