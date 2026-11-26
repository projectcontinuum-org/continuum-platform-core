package org.projectcontinuum.core.orchestration.registration

import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor

/**
 * A registered node that runs inside the orchestration service instead of on a feature worker, so it has no activity
 * and no real task queue. The orchestrator registers it in the node registry like a worker registers its nodes.
 */
data class BuiltinNodeDefinition(
  val nodeId: String,
  val nodeData: ContinuumWorkflowModel.NodeData,
  val documentationMarkdown: String,
  val categories: List<String>
)

/** The built-in nodes the orchestration service registers at start-up. Add a new built-in node here. */
object BuiltinNodes {

  /** Feature id of every built-in node: the package root of the platform's built-in node ids. */
  const val FEATURE_ID = "org.projectcontinuum.core.builtin"

  /** Task-queue marker stored in the registry for built-in nodes: they never get an activity stub. */
  const val TASK_QUEUE = TaskQueues.BUILTIN_NO_ACTIVITY

  val loop: BuiltinNodeDefinition
    get() = BuiltinNodeDefinition(
      nodeId = LoopNode.NODE_MODEL,
      nodeData = LoopNodeDescriptor.nodeData,
      documentationMarkdown = LoopNodeDescriptor.documentationMarkdown,
      categories = LoopNodeDescriptor.categories
    )

  val all: List<BuiltinNodeDefinition>
    get() = listOf(loop)
}
