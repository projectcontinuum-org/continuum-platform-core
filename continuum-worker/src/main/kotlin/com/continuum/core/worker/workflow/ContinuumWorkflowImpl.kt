package com.continuum.core.worker.workflow

import com.continuum.core.worker.TaskQueues.WORKFLOW_TASK_QUEUE
import io.temporal.spring.boot.WorkflowImpl

@WorkflowImpl(taskQueues = [WORKFLOW_TASK_QUEUE])
class ContinuumWorkflowImpl: ContinuumWorkflow {
    override fun start() {
        println("Starting ContinuumWorkflowImpl")
    }
}