package org.projectcontinuum.core.orchestration.workflow.fixtures

import org.projectcontinuum.core.commons.activity.IInitializeActivity

/**
 * Configurable fake [IInitializeActivity] that routes every requested node id
 * to a single shared task queue, optionally dropping one node id to simulate
 * partial task-queue resolution (used to trigger [ContinuumWorkflow][org.projectcontinuum.core.orchestration.workflow.ContinuumWorkflow]'s
 * "Failed to initialize activities for all nodes" failure path).
 */
class FakeInitializeActivity(
  private val taskQueueForAllNodes: String,
  private val dropOneNode: Boolean = false
) : IInitializeActivity {

  override fun getNodeTaskQueue(nodeIds: Set<String>): Map<String, String> {
    val ids = if (dropOneNode) nodeIds.drop(1) else nodeIds.toList()
    return ids.associateWith { taskQueueForAllNodes }
  }
}
