package com.continuum.core.api.server.controller

import com.continuum.core.api.server.model.WorkflowStatus
import com.continuum.core.api.server.service.WorkflowService
import com.continuum.core.api.server.utils.TreeHelper
import com.continuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/workflow")
class WorkflowController(
    val workflowService: WorkflowService
) {

    @PostMapping
    fun startWorkflow(
        @RequestBody
        continuumWorkflowModel: ContinuumWorkflowModel
    ): String {
        return workflowService.startWorkflow(continuumWorkflowModel)
    }

    @GetMapping("/active")
    fun getActiveWorkflows(): List<String> {
        return workflowService.getActiveWorkflows().map { it.workflowId }
    }

    @GetMapping("/{workflowId}/status")
    fun getWorkflowStatusById(
        @PathVariable
        workflowId: String
    ): WorkflowStatus {
        return workflowService.getWorkflowStatusById(workflowId)
    }

    @GetMapping("/list")
    fun listWorkflow(): List<WorkflowStatus> {
        return workflowService.listAllWorkflow()
    }

    @GetMapping("/tree")
    fun getWorkflowTree(
        @RequestParam
        baseDir: String
    ): List<TreeHelper.TreeItem<TreeHelper.Execution>> {
        return workflowService.getWorkflowTree(baseDir)
    }

}