package org.projectcontinuum.core.knime.scheduler.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.knime.scheduler.exception.GlobalExceptionHandler
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowScheduleNotFoundException
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowScheduleRequestInvalidException
import org.projectcontinuum.core.knime.scheduler.model.KnimeWorkflowScheduleResponse
import org.projectcontinuum.core.knime.scheduler.service.KnimeWorkflowScheduleService
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class KnimeWorkflowScheduleControllerTest {

  private val service: KnimeWorkflowScheduleService = mock()
  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(KnimeWorkflowScheduleController(service))
      .setControllerAdvice(GlobalExceptionHandler())
      .build()
  }

  private fun response(scheduleId: UUID, knimeWorkflowId: UUID, ownedBy: String = "alice"): KnimeWorkflowScheduleResponse =
    KnimeWorkflowScheduleResponse(
      scheduleId = scheduleId,
      name = "Nightly run",
      ownedBy = ownedBy,
      cronExpression = "0 0 * * *",
      timeZone = null,
      paused = false,
      nextRunTimes = emptyList(),
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      knimeWorkflowId = knimeWorkflowId,
      resetWorkflow = false,
      timeoutSeconds = 300,
      workflowVariables = emptyList(),
      workflowCredentials = emptyList()
    )

  private fun createJson(knimeWorkflowId: UUID) = """
    {
      "name": "Nightly run",
      "cronExpression": "0 0 * * *",
      "knimeWorkflowId": "$knimeWorkflowId"
    }
  """.trimIndent()

  @Test
  fun `POST create returns 201 with schedule`() {
    val scheduleId = UUID.randomUUID()
    val knimeWorkflowId = UUID.randomUUID()
    whenever(service.createSchedule(any(), eq("alice"))).thenReturn(response(scheduleId, knimeWorkflowId))

    mockMvc.perform(
      post("/api/v1/knime-workflow-schedules")
        .header("x-continuum-user-id", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .content(createJson(knimeWorkflowId))
    )
      .andExpect(status().isCreated)
      .andExpect(jsonPath("$.scheduleId").value(scheduleId.toString()))
      .andExpect(jsonPath("$.knimeWorkflowId").value(knimeWorkflowId.toString()))
  }

  @Test
  fun `POST create returns 404 when the referenced knwf is not owned by the caller`() {
    val knimeWorkflowId = UUID.randomUUID()
    whenever(service.createSchedule(any(), eq("bob")))
      .thenThrow(org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowNotFoundException(knimeWorkflowId))

    mockMvc.perform(
      post("/api/v1/knime-workflow-schedules")
        .header("x-continuum-user-id", "bob")
        .contentType(MediaType.APPLICATION_JSON)
        .content(createJson(knimeWorkflowId))
    )
      .andExpect(status().isNotFound)
  }

  @Test
  fun `POST create returns 400 when api-server rejects the schedule request`() {
    val knimeWorkflowId = UUID.randomUUID()
    whenever(service.createSchedule(any(), eq("alice")))
      .thenThrow(KnimeWorkflowScheduleRequestInvalidException("bad cron"))

    mockMvc.perform(
      post("/api/v1/knime-workflow-schedules")
        .header("x-continuum-user-id", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .content(createJson(knimeWorkflowId))
    )
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `GET by id returns 200 when found`() {
    val scheduleId = UUID.randomUUID()
    val knimeWorkflowId = UUID.randomUUID()
    whenever(service.getSchedule("alice", scheduleId)).thenReturn(response(scheduleId, knimeWorkflowId))

    mockMvc.perform(get("/api/v1/knime-workflow-schedules/$scheduleId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.scheduleId").value(scheduleId.toString()))
  }

  @Test
  fun `GET by id returns 404 when not found`() {
    val scheduleId = UUID.randomUUID()
    whenever(service.getSchedule("alice", scheduleId))
      .thenThrow(KnimeWorkflowScheduleNotFoundException("not found"))

    mockMvc.perform(get("/api/v1/knime-workflow-schedules/$scheduleId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `GET list returns schedules for owner`() {
    val scheduleId = UUID.randomUUID()
    val knimeWorkflowId = UUID.randomUUID()
    whenever(service.listSchedules("alice")).thenReturn(listOf(response(scheduleId, knimeWorkflowId)))

    mockMvc.perform(get("/api/v1/knime-workflow-schedules").header("x-continuum-user-id", "alice"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
  }

  @Test
  fun `POST pause returns 204 on success`() {
    val scheduleId = UUID.randomUUID()

    mockMvc.perform(post("/api/v1/knime-workflow-schedules/$scheduleId/pause").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)
  }

  @Test
  fun `POST pause returns 404 when schedule not found`() {
    val scheduleId = UUID.randomUUID()
    whenever(service.pauseSchedule(eq("alice"), eq(scheduleId), anyOrNull()))
      .thenThrow(KnimeWorkflowScheduleNotFoundException("not found"))

    mockMvc.perform(post("/api/v1/knime-workflow-schedules/$scheduleId/pause").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `POST unpause returns 204 on success`() {
    val scheduleId = UUID.randomUUID()

    mockMvc.perform(post("/api/v1/knime-workflow-schedules/$scheduleId/unpause").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)
  }

  @Test
  fun `POST trigger returns 204 on success`() {
    val scheduleId = UUID.randomUUID()

    mockMvc.perform(post("/api/v1/knime-workflow-schedules/$scheduleId/trigger").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)
  }

  @Test
  fun `DELETE returns 204 on success`() {
    val scheduleId = UUID.randomUUID()

    mockMvc.perform(delete("/api/v1/knime-workflow-schedules/$scheduleId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)
  }

  @Test
  fun `DELETE returns 404 when schedule not found`() {
    val scheduleId = UUID.randomUUID()
    whenever(service.deleteSchedule("alice", scheduleId))
      .thenThrow(KnimeWorkflowScheduleNotFoundException("not found"))

    mockMvc.perform(delete("/api/v1/knime-workflow-schedules/$scheduleId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
  }
}
