package org.projectcontinuum.core.cluster.manager.controller

import org.projectcontinuum.core.cluster.manager.model.WorkbenchCreateRequest
import org.projectcontinuum.core.cluster.manager.model.WorkbenchResponse
import org.projectcontinuum.core.cluster.manager.model.WorkbenchUpdateRequest
import org.projectcontinuum.core.cluster.manager.service.DockerHubService
import org.projectcontinuum.core.cluster.manager.service.DockerHubTag
import org.projectcontinuum.core.cluster.manager.service.WorkbenchService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/workbench")
class WorkbenchController(
  private val workbenchService: WorkbenchService,
  private val dockerHubService: DockerHubService
) {

  @GetMapping("/tags")
  fun getAvailableTags(): ResponseEntity<List<DockerHubTag>> {
    val tags = dockerHubService.getAvailableTags()
    return ResponseEntity.ok(tags)
  }

  @PostMapping
  fun createWorkbench(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @RequestBody request: WorkbenchCreateRequest
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.createWorkbench(userId, request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }

  @GetMapping("/{instanceName}")
  fun getWorkbenchStatus(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable instanceName: String
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.getWorkbenchStatus(userId, instanceName)
    return ResponseEntity.ok(response)
  }

  @DeleteMapping("/{instanceName}")
  fun deleteWorkbench(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable instanceName: String
  ): ResponseEntity<Void> {
    workbenchService.deleteWorkbench(userId, instanceName)
    return ResponseEntity.noContent().build()
  }

  @GetMapping
  fun listWorkbenches(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String
  ): ResponseEntity<List<WorkbenchResponse>> {
    val response = workbenchService.listWorkbenches(userId)
    return ResponseEntity.ok(response)
  }

  @PutMapping("/{instanceName}/suspend")
  fun suspendWorkbench(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable instanceName: String
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.suspendWorkbench(userId, instanceName)
    return ResponseEntity.ok(response)
  }

  @PutMapping("/{instanceName}/resume")
  fun resumeWorkbench(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable instanceName: String
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.resumeWorkbench(userId, instanceName)
    return ResponseEntity.ok(response)
  }

  @PutMapping("/{instanceName}")
  fun updateWorkbench(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @PathVariable instanceName: String,
    @RequestBody request: WorkbenchUpdateRequest
  ): ResponseEntity<WorkbenchResponse> {
    val response = workbenchService.updateWorkbench(userId, instanceName, request)
    return ResponseEntity.ok(response)
  }
}
