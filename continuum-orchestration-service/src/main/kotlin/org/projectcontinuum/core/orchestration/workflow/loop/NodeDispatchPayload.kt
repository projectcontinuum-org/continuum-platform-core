package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

/**
 * The [ContinuumWorkflowModel.Node] a worker receives when a node is dispatched. The loop fields
 * `parentNode` and `data.collapsed` describe the editor's loop boxes and mean nothing to a worker, so they are stripped:
 * a worker built on an older `continuum-commons` never sees them. A node without them is passed through unchanged (the
 * same instance), so loop-free workflows dispatch exactly as before.
 */
object NodeDispatchPayload {

  fun of(node: ContinuumWorkflowModel.Node): ContinuumWorkflowModel.Node {
    val hasLoopFields = node.parentNode != null || node.data.collapsed != null
    if (!hasLoopFields) return node
    return node.copy(parentNode = null, data = node.data.copy(collapsed = null))
  }
}
