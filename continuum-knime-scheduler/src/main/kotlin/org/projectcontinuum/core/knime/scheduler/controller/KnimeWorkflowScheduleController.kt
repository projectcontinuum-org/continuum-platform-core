package org.projectcontinuum.core.knime.scheduler.controller

import org.projectcontinuum.core.knime.scheduler.model.CreateKnimeWorkflowScheduleRequest
import org.projectcontinuum.core.knime.scheduler.model.KnimeWorkflowScheduleResponse
import org.projectcontinuum.core.knime.scheduler.service.KnimeWorkflowScheduleService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val USER_ID_HEADER = "x-continuum-user-id"

@RestController
@RequestMapping("/api/v1/knime-workflow-schedules")
class KnimeWorkflowScheduleController(
  private val knimeWorkflowScheduleService: KnimeWorkflowScheduleService
) {

  @PostMapping
  fun create(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @RequestBody request: CreateKnimeWorkflowScheduleRequest
  ): ResponseEntity<KnimeWorkflowScheduleResponse> {
    val response = knimeWorkflowScheduleService.createSchedule(request, userId)
    return ResponseEntity.status(201).body(response)
  }

  @GetMapping
  fun list(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String
  ): List<KnimeWorkflowScheduleResponse> = knimeWorkflowScheduleService.listSchedules(userId)

  @GetMapping("/{scheduleId}")
  fun get(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @PathVariable scheduleId: UUID
  ): KnimeWorkflowScheduleResponse = knimeWorkflowScheduleService.getSchedule(userId, scheduleId)

  @PostMapping("/{scheduleId}/pause")
  fun pause(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @PathVariable scheduleId: UUID,
    @RequestParam(required = false) note: String?
  ): ResponseEntity<Void> {
    knimeWorkflowScheduleService.pauseSchedule(userId, scheduleId, note)
    return ResponseEntity.noContent().build()
  }

  @PostMapping("/{scheduleId}/unpause")
  fun unpause(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @PathVariable scheduleId: UUID,
    @RequestParam(required = false) note: String?
  ): ResponseEntity<Void> {
    knimeWorkflowScheduleService.unpauseSchedule(userId, scheduleId, note)
    return ResponseEntity.noContent().build()
  }

  @PostMapping("/{scheduleId}/trigger")
  fun trigger(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @PathVariable scheduleId: UUID
  ): ResponseEntity<Void> {
    knimeWorkflowScheduleService.triggerNow(userId, scheduleId)
    return ResponseEntity.noContent().build()
  }

  @DeleteMapping("/{scheduleId}")
  fun delete(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @PathVariable scheduleId: UUID
  ): ResponseEntity<Void> {
    knimeWorkflowScheduleService.deleteSchedule(userId, scheduleId)
    return ResponseEntity.noContent().build()
  }
}
