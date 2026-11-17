package org.projectcontinuum.core.api.server.controller

import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.client.WorkflowNotFoundException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.service.WorkflowService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(WorkflowController::class)
class WorkflowControllerOwnershipTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var workflowService: WorkflowService

  private fun notFound() =
    WorkflowNotFoundException(WorkflowExecution.newBuilder().setWorkflowId("run-1").build(), null, null)

  @Test
  fun `cancel passes the caller as owner and returns 204 for the owner`() {
    mockMvc.perform(post("/api/v1/workflow/run-1/cancel").param("reason", "done").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)

    verify(workflowService).cancelWorkflow(eq("alice"), eq("run-1"), eq("done"))
  }

  @Test
  fun `cancel of a workflow the caller does not own is 404`() {
    whenever(workflowService.cancelWorkflow(eq("mallory"), eq("run-1"), isNull())).thenThrow(notFound())

    mockMvc.perform(post("/api/v1/workflow/run-1/cancel").header("x-continuum-user-id", "mallory"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `terminate passes the caller as owner and returns 204 for the owner`() {
    mockMvc.perform(post("/api/v1/workflow/run-1/terminate").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNoContent)

    verify(workflowService).terminateWorkflow(eq("alice"), eq("run-1"), isNull())
  }

  @Test
  fun `terminate of a workflow the caller does not own is 404`() {
    whenever(workflowService.terminateWorkflow(eq("mallory"), eq("run-1"), isNull())).thenThrow(notFound())

    mockMvc.perform(post("/api/v1/workflow/run-1/terminate").header("x-continuum-user-id", "mallory"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `cancel and terminate without the header act as anonymous`() {
    mockMvc.perform(post("/api/v1/workflow/run-1/cancel")).andExpect(status().isNoContent)
    mockMvc.perform(post("/api/v1/workflow/run-1/terminate")).andExpect(status().isNoContent)

    verify(workflowService).cancelWorkflow(eq("anonymous"), eq("run-1"), isNull())
    verify(workflowService).terminateWorkflow(eq("anonymous"), eq("run-1"), isNull())
  }

  @Test
  fun `active workflows are requested for the calling owner only`() {
    whenever(workflowService.getActiveWorkflows(any())).thenReturn(emptyList())

    mockMvc.perform(get("/api/v1/workflow/active").header("x-continuum-user-id", "alice"))
      .andExpect(status().isOk)
      .andExpect(content().json("[]"))

    verify(workflowService).getActiveWorkflows(eq("alice"))
  }
}
