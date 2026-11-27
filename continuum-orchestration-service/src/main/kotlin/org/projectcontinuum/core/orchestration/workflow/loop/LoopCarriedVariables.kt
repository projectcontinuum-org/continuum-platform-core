package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.utils.FlowVariables

/**
 * The variables a loop carries from one iteration into the next. Pure; the loop runner keeps the result per loop and
 * hands it to
 * [org.projectcontinuum.core.orchestration.workflow.FlowVariableMerger] as `carriedByLoopId`.
 *
 * After an iteration, the carried set is the previous carried set overridden by the own variables of every member of
 * the loop (at any depth, nested loops' members included) that produced outputs in that iteration, applied in the
 * loop's run order ([LoopTopology.topologicalOrder]), so on a name clash the member that runs later wins. A variable
 * set in an earlier iteration and not set again stays carried. Before the first iteration the set is empty.
 *
 * The runner clears the members' outputs between iterations, so [nodeToOutputsMap] holds exactly this iteration's
 * outputs of the members.
 */
internal object LoopCarriedVariables {

  fun afterIteration(
    loopId: String,
    loopTopology: LoopTopology,
    previouslyCarried: Map<String, Any>,
    nodeToOutputsMap: Map<String, Map<String, PortData>>
  ): Map<String, Any> =
    loopTopology.topologicalOrder(loopId = loopId)
      .mapNotNull { memberId -> nodeToOutputsMap[memberId] }
      .fold(initial = previouslyCarried) { carried, memberOutputs -> carried + FlowVariables.extract(memberOutputs) }
      .minus(FlowVariables.ITERATION)
}
