package org.projectcontinuum.core.api.server.controller

import io.temporal.client.schedules.ScheduleException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.model.WorkflowScheduleResponse
import org.projectcontinuum.core.api.server.service.WorkflowScheduleNotFoundException
import org.projectcontinuum.core.api.server.service.WorkflowScheduleService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(WorkflowScheduleController::class)
class WorkflowScheduleControllerTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var workflowScheduleService: WorkflowScheduleService

  private fun response(scheduleId: UUID, ownedBy: String = "anonymous"): WorkflowScheduleResponse =
    WorkflowScheduleResponse(
      scheduleId = scheduleId,
      name = "Hourly run",
      ownedBy = ownedBy,
      cronExpression = "0 * * * *",
      timeZone = null,
      paused = false,
      nextRunTimes = emptyList(),
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )

  private val workflowJson = """
    {
      "name": "Hourly run",
      "cronExpression": "0 * * * *",
      "continuumWorkflowModel": {
        "id": "wf-1",
        "name": "My Workflow",
        "nodes": [],
        "edges": []
      }
    }
  """.trimIndent()

  @Test
  fun `POST schedule creates a schedule and passes owner header through`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.createSchedule(any(), eq("alice"))).thenReturn(response(scheduleId, "alice"))

    mockMvc.perform(
      post("/api/v1/workflow/schedule")
        .header("x-continuum-user-id", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .content(workflowJson)
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.scheduleId").value(scheduleId.toString()))
      .andExpect(jsonPath("$.ownedBy").value("alice"))
  }

  @Test
  fun `POST schedule defaults owner to anonymous when header omitted`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.createSchedule(any(), eq("anonymous"))).thenReturn(response(scheduleId))

    mockMvc.perform(
      post("/api/v1/workflow/schedule")
        .contentType(MediaType.APPLICATION_JSON)
        .content(workflowJson)
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.ownedBy").value("anonymous"))
  }

  @Test
  fun `POST schedule with ScheduleException returns 400`() {
    whenever(workflowScheduleService.createSchedule(any(), any())).thenThrow(ScheduleException(RuntimeException("bad cron")))

    mockMvc.perform(
      post("/api/v1/workflow/schedule")
        .contentType(MediaType.APPLICATION_JSON)
        .content(workflowJson)
    )
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `GET schedule list returns schedules for owner`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.listSchedules("alice")).thenReturn(listOf(response(scheduleId, "alice")))

    mockMvc.perform(get("/api/v1/workflow/schedule").header("x-continuum-user-id", "alice"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].scheduleId").value(scheduleId.toString()))
  }

  @Test
  fun `GET schedule list filters by name query param`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.listSchedules("alice", "My Workflow")).thenReturn(listOf(response(scheduleId, "alice")))

    mockMvc.perform(
      get("/api/v1/workflow/schedule")
        .header("x-continuum-user-id", "alice")
        .param("name", "My Workflow")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].scheduleId").value(scheduleId.toString()))
  }

  @Test
  fun `GET schedule list by RSQL filter returns filtered results`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.listSchedulesByRsql(eq("alice"), eq("name==My Workflow"))).thenReturn(listOf(response(scheduleId, "alice")))

    mockMvc.perform(
      get("/api/v1/workflow/schedule/filter")
        .header("x-continuum-user-id", "alice")
        .param("filter", "name==My Workflow")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
  }

  @Test
  fun `GET schedule list by RSQL filter with multiple conditions`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.listSchedulesByRsql(eq("alice"), eq("name==~.*Hourly.*;createdAt>2024-01-01"))).thenReturn(listOf(response(scheduleId, "alice")))

    mockMvc.perform(
      get("/api/v1/workflow/schedule/filter")
        .header("x-continuum-user-id", "alice")
        .param("filter", "name==~.*Hourly.*;createdAt>2024-01-01")
    )
      .andExpect(status().isOk)
  }

  @Test
  fun `GET schedule list by RSQL filter without filter returns all for owner`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.listSchedulesByRsql(eq("alice"), anyOrNull())).thenReturn(listOf(response(scheduleId, "alice")))

    mockMvc.perform(
      get("/api/v1/workflow/schedule/filter")
        .header("x-continuum-user-id", "alice")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
  }

  @Test
  fun `GET schedule by id returns 200 when found`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.getSchedule("alice", scheduleId)).thenReturn(response(scheduleId, "alice"))

    mockMvc.perform(get("/api/v1/workflow/schedule/$scheduleId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.scheduleId").value(scheduleId.toString()))
  }

  @Test
  fun `GET schedule by id returns 404 when not found`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.getSchedule("alice", scheduleId)).thenReturn(null)

    mockMvc.perform(get("/api/v1/workflow/schedule/$scheduleId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `POST pause returns 204 on success`() {
    val scheduleId = UUID.randomUUID()

    mockMvc.perform(post("/api/v1/workflow/schedule/$scheduleId/pause").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)
  }

  @Test
  fun `POST pause returns 404 when schedule not found`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.pauseSchedule(eq("alice"), eq(scheduleId), anyOrNull()))
      .thenThrow(WorkflowScheduleNotFoundException("not found"))

    mockMvc.perform(post("/api/v1/workflow/schedule/$scheduleId/pause").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `POST unpause returns 204 on success`() {
    val scheduleId = UUID.randomUUID()

    mockMvc.perform(post("/api/v1/workflow/schedule/$scheduleId/unpause").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)
  }

  @Test
  fun `POST unpause returns 404 when schedule not found`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.unpauseSchedule(eq("alice"), eq(scheduleId), anyOrNull()))
      .thenThrow(WorkflowScheduleNotFoundException("not found"))

    mockMvc.perform(post("/api/v1/workflow/schedule/$scheduleId/unpause").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `POST trigger returns 204 on success`() {
    val scheduleId = UUID.randomUUID()

    mockMvc.perform(post("/api/v1/workflow/schedule/$scheduleId/trigger").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)
  }

  @Test
  fun `POST trigger returns 404 when schedule not found`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.triggerNow("alice", scheduleId))
      .thenThrow(WorkflowScheduleNotFoundException("not found"))

    mockMvc.perform(post("/api/v1/workflow/schedule/$scheduleId/trigger").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `DELETE schedule returns 204 on success`() {
    val scheduleId = UUID.randomUUID()

    mockMvc.perform(delete("/api/v1/workflow/schedule/$scheduleId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)
  }

  @Test
  fun `DELETE schedule returns 404 when schedule not found`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.deleteSchedule("alice", scheduleId))
      .thenThrow(WorkflowScheduleNotFoundException("not found"))

    mockMvc.perform(delete("/api/v1/workflow/schedule/$scheduleId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
  }
}
