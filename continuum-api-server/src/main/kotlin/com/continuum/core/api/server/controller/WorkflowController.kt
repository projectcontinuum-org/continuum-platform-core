package com.continuum.core.api.server.controller

import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.workflow.IContinuumWorkflow
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/workflow")
class WorkflowController(
  val workflowClient: WorkflowClient
) {

  @PostMapping("/start")
  fun startWorkflow(
    @RequestBody
    continuumWorkflowModel: ContinuumWorkflowModel
  ): String {
    val continuumWorkflow = workflowClient.newWorkflowStub(
      IContinuumWorkflow::class.java,
      WorkflowOptions.newBuilder()
        .setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE)
        .build()
    )
    continuumWorkflow.start(continuumWorkflowModel)
    return "Started workflow"
  }

}