package org.projectcontinuum.core.commons.model

/**
 * Constants of the built-in Loop node: a box around member nodes that the orchestrator re-runs while a condition holds.
 *
 * A loop is an ordinary entry of [ContinuumWorkflowModel.nodes] with React Flow `type` [NODE_TYPE] and
 * `data.nodeModel` [NODE_MODEL]. Its members point at it with [ContinuumWorkflowModel.Node.parentNode]. It has no ports
 * and no activity: the orchestration service registers it (task queue marker
 * [org.projectcontinuum.core.commons.constant.TaskQueues.BUILTIN_NO_ACTIVITY]) and runs it itself.
 */
object LoopNode {
  /** React Flow node type of a loop box (`Node.type`). */
  const val NODE_TYPE = "Loop"

  /** Registered node id of the loop (`Node.data.nodeModel`, also `Node.data.id`). */
  const val NODE_MODEL = "org.projectcontinuum.core.builtin.Loop"

  /** Palette category the loop is registered under. */
  const val CATEGORY = "Flow Control"

  /** True when [node] is a loop, decided by its registered node id (not by its React Flow type). */
  fun isLoop(node: ContinuumWorkflowModel.Node): Boolean = node.data.nodeModel == NODE_MODEL
}
