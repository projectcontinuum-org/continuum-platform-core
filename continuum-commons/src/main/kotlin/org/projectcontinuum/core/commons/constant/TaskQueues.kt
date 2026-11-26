package org.projectcontinuum.core.commons.constant

// Helper "enum" of the different task queue keywords in use.
// Couldn't use kotlin enum as you need to use the string form available via `MyEnum.enumMember.name`, which is not a constant.

object TaskQueues {
  const val WORKFLOW_TASK_QUEUE = "WORKFLOW_TASK_QUEUE"
  const val ACTIVITY_TASK_QUEUE = "ACTIVITY_TASK_QUEUE"
  const val ACTIVITY_TASK_QUEUE_INITIALIZE = "ACTIVITY_TASK_QUEUE_INITIALIZE"

  /**
   * Task-queue marker of registered nodes that run inside the orchestrator and have no activity (the built-in Loop).
   * Never a real Temporal task queue: the orchestrator does not request or dispatch to it.
   */
  const val BUILTIN_NO_ACTIVITY = "\$builtin"
}
