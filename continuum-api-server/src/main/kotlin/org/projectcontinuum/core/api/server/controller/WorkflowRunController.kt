package org.projectcontinuum.core.api.server.controller

import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunSummaryEntity
import org.projectcontinuum.core.api.server.service.WorkflowRunService
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/workflow-runs")
class WorkflowRunController(
  val workflowRunService: WorkflowRunService
) {

  @GetMapping
  fun findAll(
    @RequestHeader("x-continuum-user-id") ownedBy: String,
    @RequestParam(required = false) filter: String?,
    @RequestParam(required = false, defaultValue = "0") page: Int,
    @RequestParam(required = false, defaultValue = "20") size: Int,
    @RequestParam(required = false) sort: String?
  ): Page<WorkflowRunSummaryEntity> {
    return workflowRunService.findAll(ownedBy, filter, page, size, sort)
  }

  @GetMapping("/{workflowId}")
  fun findById(
    @RequestHeader("x-continuum-user-id") ownedBy: String,
    @PathVariable workflowId: UUID
  ): ResponseEntity<WorkflowRunEntity> {
    val entity = workflowRunService.findById(ownedBy, workflowId)
      ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(entity)
  }

  @GetMapping("/distinct-workflows")
  fun findDistinctWorkflows(
    @RequestHeader("x-continuum-user-id") ownedBy: String,
    @RequestParam(required = false) filter: String?,
    @RequestParam(required = false, defaultValue = "0") page: Int,
    @RequestParam(required = false, defaultValue = "50") size: Int
  ): Page<String> {
    return workflowRunService.findDistinctWorkflows(ownedBy, filter, page, size)
  }

  @GetMapping("/count")
  fun count(
    @RequestHeader("x-continuum-user-id") ownedBy: String,
    @RequestParam(required = false) filter: String?
  ): Map<String, Long> {
    return mapOf("count" to workflowRunService.count(ownedBy, filter))
  }
}
