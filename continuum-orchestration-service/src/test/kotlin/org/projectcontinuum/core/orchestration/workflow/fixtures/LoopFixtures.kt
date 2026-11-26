package org.projectcontinuum.core.orchestration.workflow.fixtures

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode

/**
 * The paging example as a run model: seed, then fetch, parse and append inside loop L, then write. [loopProperties]
 * override the loop's defaults from the fixture file.
 */
object LoopFixtures {

  const val LINEAR_FIXTURE = "/test-workflows/loop-linear-3member.cwf.json"

  fun pagingWorkflow(loopProperties: Map<String, Any> = emptyMap()): ContinuumWorkflowModel =
    withLoopProperties(workflow = WorkflowFixtures.loadWorkflow(classpathResource = LINEAR_FIXTURE), loopProperties = loopProperties)

  fun withLoopProperties(
    workflow: ContinuumWorkflowModel,
    loopProperties: Map<String, Any>,
    loopId: String? = null
  ): ContinuumWorkflowModel = workflow.copy(
    nodes = workflow.nodes.map { node ->
      val isTarget = LoopNode.isLoop(node = node) && (loopId == null || node.id == loopId)
      if (isTarget) node.copy(data = node.data.copy(properties = node.data.properties + loopProperties)) else node
    }
  )
}
