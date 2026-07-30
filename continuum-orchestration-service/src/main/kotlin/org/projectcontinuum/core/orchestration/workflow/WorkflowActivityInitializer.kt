package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.activity.IInitializeActivity
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.failure.ApplicationFailure
import io.temporal.workflow.Workflow
import java.time.Duration

/**
 * Resolves per-node task queues and builds the [IContinuumNodeActivity] activity stubs
 * a [ContinuumWorkflow] dispatches node execution to.
 */
class WorkflowActivityInitializer {

  /**
   * Retry options for activity execution.
   * Configures exponential backoff with a maximum of 500 attempts.
   */
  private val retryOptions: RetryOptions = RetryOptions {
    setMaximumInterval(Duration.ofSeconds(100))
    setBackoffCoefficient(2.0)
    setMaximumAttempts(500)
  }

  /**
   * Base activity options for node execution.
   * Activities can run for up to 60 days with the configured retry policy.
   */
  private val baseActivityOptions: ActivityOptions = ActivityOptions {
    setStartToCloseTimeout(Duration.ofDays(60))
    setRetryOptions(retryOptions)
    setTaskQueue(TaskQueues.ACTIVITY_TASK_QUEUE)
  }

  /**
   * Base activity options for node execution.
   * Activities can run for up to 60 days with the configured retry policy.
   */
  private val initializeActivityOptions: ActivityOptions = ActivityOptions {
    setStartToCloseTimeout(Duration.ofDays(60))
    setRetryOptions(retryOptions)
    setTaskQueue(TaskQueues.ACTIVITY_TASK_QUEUE_INITIALIZE)
  }

  /** Activity stub for executing initialization activities (e.g., fetching task queues) */
  private val initializeActivity = Workflow.newActivityStub(
    IInitializeActivity::class.java,
    ActivityOptions {
      mergeActivityOptions(initializeActivityOptions)
    }
  )

  /**
   * Initializes activity stubs for each node by fetching task queue assignments from the API server.
   *
   * Queries the [IInitializeActivity] to resolve each node type's task queue, then creates a
   * dedicated [IContinuumNodeActivity] stub per DAG node instance (keyed by [ContinuumWorkflowModel.Node.id],
   * not the node type) routed to the correct task queue, with per-node retry overrides applied.
   *
   * @param continuumWorkflow The workflow model containing the nodes to initialize
   * @return Map of node instance IDs to their configured activity stubs
   * @throws ApplicationFailure if not all nodes could be resolved to a task queue
   */
  fun initializeNodeActivityStubs(
    continuumWorkflow: ContinuumWorkflowModel
  ): Map<String, IContinuumNodeActivity> {
    val uniqueNodeIds = continuumWorkflow.nodes.map { it.data.id!! }.toSet()
    val nodeTypeToTaskQueueMap = initializeActivity.getNodeTaskQueue(uniqueNodeIds)

    val activityMap = continuumWorkflow.nodes.mapNotNull { node ->
      val taskQueue = nodeTypeToTaskQueueMap[node.data.id!!] ?: return@mapNotNull null
      val nodeRetryOptions = buildNodeRetryOptions(node.data.retryOptions)
      val activityStub = Workflow.newActivityStub(
        IContinuumNodeActivity::class.java,
        ActivityOptions {
          mergeActivityOptions(baseActivityOptions)
          setTaskQueue(taskQueue)
          setHeartbeatTimeout(Duration.ofMinutes(5))
          setRetryOptions(nodeRetryOptions)
        }
      )
      node.id to activityStub
    }.toMap()

    if (activityMap.size != continuumWorkflow.nodes.size) {
      throw ApplicationFailure.newNonRetryableFailure(
        "Failed to initialize activities for all nodes. Expected ${continuumWorkflow.nodes.size} but got ${activityMap.size}",
        "InitializationFailed"
      )
    }

    return activityMap
  }

  /**
   * Builds the effective [RetryOptions] for a single node, applying any node-level override
   * on top of the workflow-wide default [retryOptions]. Fields left null on the override are
   * inherited from the default; fields explicitly set (including maximumAttempts = 0, which
   * Temporal treats as "unlimited") take precedence. Built field-by-field via explicit null
   * checks rather than RetryOptions.merge(), which cannot distinguish "not set" from an
   * intentionally-set Java-default value (e.g. maximumAttempts = 0).
   */
  private fun buildNodeRetryOptions(
    override: ContinuumWorkflowModel.RetryOptionsConfig?
  ): RetryOptions {
    if (override == null) return retryOptions
    val builder = RetryOptions.newBuilder(retryOptions)
    override.initialIntervalSeconds?.let { builder.setInitialInterval(Duration.ofSeconds(it)) }
    override.backoffCoefficient?.let { builder.setBackoffCoefficient(it) }
    override.maximumIntervalSeconds?.let { builder.setMaximumInterval(Duration.ofSeconds(it)) }
    override.maximumAttempts?.let { builder.setMaximumAttempts(it) }
    override.doNotRetry?.let { builder.setDoNotRetry(*it.toTypedArray()) }
    return builder.build()
  }
}
