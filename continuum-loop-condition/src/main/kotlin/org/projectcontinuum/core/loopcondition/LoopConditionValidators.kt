package org.projectcontinuum.core.loopcondition

import org.projectcontinuum.core.commons.validation.loop.LoopConditionValidator

/**
 * The validator the platform uses for loop conditions. The api-server passes it to `continuum-commons`'
 * `WorkflowValidator`, and the orchestrator re-checks every condition with it when a loop starts; commons itself never
 * depends on cel-java.
 */
object LoopConditionValidators {
  /** Shared, thread-safe instance of the cel-java validator. */
  val default: LoopConditionValidator by lazy { CelLoopConditionValidator() }

  /** The same instance with its internal analysis, for the evaluator in this module. */
  internal val cel: CelLoopConditionValidator get() = default as CelLoopConditionValidator
}
