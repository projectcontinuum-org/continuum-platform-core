package com.continuum.core.commons.constant

// Helper "enum" of the different task queue keywords in use.
// Couldn't use kotlin enum as you need to use the string form available via `MyEnum.enumMember.name`, which is not a constant.

object TaskQueues {
  const val WORKFLOW_TASK_QUEUE = "WORKFLOW_TASK_QUEUE"
  const val ACTIVITY_TASK_QUEUE = "ACTIVITY_TASK_QUEUE"
}