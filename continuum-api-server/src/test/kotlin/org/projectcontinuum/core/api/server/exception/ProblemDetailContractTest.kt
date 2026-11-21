package org.projectcontinuum.core.api.server.exception

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.controller.WorkflowRunController
import org.projectcontinuum.core.api.server.controller.WorkflowScheduleController
import org.projectcontinuum.core.api.server.service.WorkflowRunService
import org.projectcontinuum.core.api.server.service.WorkflowScheduleService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** Every error of the API server is an `application/problem+json` body, including those that used to be empty. */
@WebMvcTest(WorkflowRunController::class, WorkflowScheduleController::class)
class ProblemDetailContractTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var workflowRunService: WorkflowRunService

  @MockitoBean
  private lateinit var workflowScheduleService: WorkflowScheduleService

  @Test
  fun `unknown run is a 404 problem`() {
    whenever(workflowRunService.findById(eq("alice"), eq("missing"))).thenReturn(null)

    mockMvc.perform(get("/api/v1/workflow-runs/missing").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(404))
      .andExpect(jsonPath("$.detail").value("Workflow run not found"))
  }

  @Test
  fun `unknown schedule is a 404 problem that does not echo the owner`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleService.getSchedule(eq("alice"), eq(scheduleId))).thenReturn(null)

    mockMvc.perform(get("/api/v1/workflow/schedule/$scheduleId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("Schedule not found"))
  }

  @Test
  fun `a schedule id that is not a UUID is a 400 problem`() {
    mockMvc.perform(get("/api/v1/workflow/schedule/not-a-uuid"))
      .andExpect(status().isBadRequest)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(400))
  }

  @Test
  fun `a malformed body is a 400 problem`() {
    mockMvc.perform(post("/api/v1/workflow/schedule").contentType(MediaType.APPLICATION_JSON).content("{not json"))
      .andExpect(status().isBadRequest)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
  }

  @Test
  fun `an unexpected failure is a 500 problem with a generic detail`() {
    whenever(workflowRunService.count(any(), anyOrNull())).thenThrow(IllegalStateException("database password is wrong"))

    mockMvc.perform(get("/api/v1/workflow-runs/count"))
      .andExpect(status().isInternalServerError)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("Internal server error"))
  }

  @Test
  fun `run count is a typed object`() {
    whenever(workflowRunService.count(eq("anonymous"), anyOrNull())).thenReturn(7L)

    mockMvc.perform(get("/api/v1/workflow-runs/count").param("filter", "status==FAILED"))
      .andExpect(status().isOk)
      .andExpect(content().json("""{"count": 7}""", true))
  }
}
