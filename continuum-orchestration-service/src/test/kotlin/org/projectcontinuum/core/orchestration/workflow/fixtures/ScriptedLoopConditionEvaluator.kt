package org.projectcontinuum.core.orchestration.workflow.fixtures

import org.projectcontinuum.core.commons.validation.loop.CompiledLoopCondition
import org.projectcontinuum.core.commons.validation.loop.LoopConditionEvaluator
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import org.projectcontinuum.core.commons.validation.loop.LoopConditionResultType
import java.util.Collections

/**
 * A test-only [LoopConditionEvaluator] that needs no CEL engine: [compile] parses nothing, and every [evaluate] call
 * returns the outcome [decide] gives for the variables (by default the next entry of [scriptedOutcomes], then `False`).
 * Records every variable map it saw, so tests can assert what the condition read.
 */
class ScriptedLoopConditionEvaluator(
  private val scriptedOutcomes: List<LoopConditionOutcome> = emptyList(),
  private val decide: ((variables: Map<String, Any?>, callIndex: Int) -> LoopConditionOutcome)? = null
) : LoopConditionEvaluator {

  /** The variables of every evaluation, in call order. */
  val seenVariables: MutableList<Map<String, Any?>> = Collections.synchronizedList(mutableListOf())

  /** The condition texts compiled, in call order. */
  val compiledTexts: MutableList<String> = Collections.synchronizedList(mutableListOf())

  override fun compile(text: String): CompiledLoopCondition {
    compiledTexts += text
    return CompiledLoopCondition(text = text, resultType = LoopConditionResultType.DYN, readVariables = emptySet(), engineHandle = null)
  }

  override fun evaluate(compiled: CompiledLoopCondition, variables: Map<String, Any?>): LoopConditionOutcome {
    val callIndex = seenVariables.size
    seenVariables += variables.toMap()
    decide?.let { decision -> return decision(variables, callIndex) }
    return scriptedOutcomes.getOrElse(callIndex) { LoopConditionOutcome.False }
  }

  override fun warmUp() = Unit

  companion object {
    /** True while the boolean flow variable [name] is true; false when it is false or missing. */
    fun whileTrue(name: String) = ScriptedLoopConditionEvaluator(decide = { variables, _ ->
      if (variables[name] == true) LoopConditionOutcome.True else LoopConditionOutcome.False
    })
  }
}
