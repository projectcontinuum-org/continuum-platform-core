package org.projectcontinuum.core.api.server.controller

import io.temporal.client.schedules.ScheduleException
import org.projectcontinuum.core.api.server.model.CreateWorkflowScheduleRequest
import org.projectcontinuum.core.api.server.model.WorkflowScheduleResponse
import org.projectcontinuum.core.api.server.service.WorkflowScheduleNotFoundException
import org.projectcontinuum.core.api.server.service.WorkflowScheduleService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/workflow/schedule")
class WorkflowScheduleController(
  private val workflowScheduleService: WorkflowScheduleService
) {

  @PostMapping
  fun createSchedule(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @RequestBody request: CreateWorkflowScheduleRequest
  ): ResponseEntity<WorkflowScheduleResponse> = try {
    ResponseEntity.ok(workflowScheduleService.createSchedule(request, ownedBy))
  } catch (e: ScheduleException) {
    ResponseEntity.badRequest().build()
  }

  @GetMapping
  fun listSchedules(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @RequestParam(required = false) name: String?
  ): List<WorkflowScheduleResponse> = workflowScheduleService.listSchedules(ownedBy, name)

  @GetMapping("/filter")
  fun listSchedulesByRsql(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @RequestParam(required = false) filter: String?
  ): List<WorkflowScheduleResponse> = workflowScheduleService.listSchedulesByRsql(ownedBy, filter)

  @GetMapping("/{scheduleId}")
  fun getSchedule(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @PathVariable scheduleId: UUID
  ): ResponseEntity<WorkflowScheduleResponse> =
    workflowScheduleService.getSchedule(ownedBy, scheduleId)
      ?.let { ResponseEntity.ok(it) }
      ?: ResponseEntity.notFound().build()

  @PostMapping("/{scheduleId}/pause")
  fun pauseSchedule(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @PathVariable scheduleId: UUID,
    @RequestParam(required = false) note: String?
  ): ResponseEntity<Void> = try {
    workflowScheduleService.pauseSchedule(ownedBy, scheduleId, note)
    ResponseEntity.noContent().build()
  } catch (e: WorkflowScheduleNotFoundException) {
    ResponseEntity.notFound().build()
  }

  @PostMapping("/{scheduleId}/unpause")
  fun unpauseSchedule(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @PathVariable scheduleId: UUID,
    @RequestParam(required = false) note: String?
  ): ResponseEntity<Void> = try {
    workflowScheduleService.unpauseSchedule(ownedBy, scheduleId, note)
    ResponseEntity.noContent().build()
  } catch (e: WorkflowScheduleNotFoundException) {
    ResponseEntity.notFound().build()
  }

  @PostMapping("/{scheduleId}/trigger")
  fun triggerNow(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @PathVariable scheduleId: UUID
  ): ResponseEntity<Void> = try {
    workflowScheduleService.triggerNow(ownedBy, scheduleId)
    ResponseEntity.noContent().build()
  } catch (e: WorkflowScheduleNotFoundException) {
    ResponseEntity.notFound().build()
  }

  @DeleteMapping("/{scheduleId}")
  fun deleteSchedule(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @PathVariable scheduleId: UUID
  ): ResponseEntity<Void> = try {
    workflowScheduleService.deleteSchedule(ownedBy, scheduleId)
    ResponseEntity.noContent().build()
  } catch (e: WorkflowScheduleNotFoundException) {
    ResponseEntity.notFound().build()
  }
}
