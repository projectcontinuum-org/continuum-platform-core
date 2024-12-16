package com.continuum.core.worker

// Helper "enum" of the different task queue keywords in use.
// Couldn't use kotlin enum as you need to use the string form available via `MyEnum.enumMember.name`, which is not a constant.

object TaskQueues {
  const val WORKFLOW_TASK_QUEUE = "WORKFLOW_TASK_QUEUE"
}