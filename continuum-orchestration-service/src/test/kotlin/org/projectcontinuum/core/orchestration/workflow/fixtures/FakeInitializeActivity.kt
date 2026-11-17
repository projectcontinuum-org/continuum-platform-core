package org.projectcontinuum.core.orchestration.workflow.fixtures

import org.projectcontinuum.core.commons.activity.IInitializeActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Configurable fake [IInitializeActivity] that routes every requested node id
 * to a single shared task queue, optionally dropping one node id to simulate
 * partial task-queue resolution (used to trigger [ContinuumWorkflow][org.projectcontinuum.core.orchestration.workflow.ContinuumWorkflow]'s
 * "Failed to initialize activities for all nodes" failure path).
 *
 * [gate], when set, holds the activity until it is released. That parks the workflow inside `start()` before it
 * has stored the workflow model, which is where a signal sent right after the start is delivered.
 */
class FakeInitializeActivity(
  private val taskQueueForAllNodes: String,
  private val dropOneNode: Boolean = false,
  private val gate: CountDownLatch? = null
) : IInitializeActivity {

  override fun getNodeTaskQueue(nodeIds: Set<String>): Map<String, String> {
    gate?.await(30, TimeUnit.SECONDS)
    val ids = if (dropOneNode) nodeIds.drop(1) else nodeIds.toList()
    return ids.associateWith { taskQueueForAllNodes }
  }
}
