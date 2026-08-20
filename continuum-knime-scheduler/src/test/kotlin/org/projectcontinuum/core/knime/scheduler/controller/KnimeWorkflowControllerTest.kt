package org.projectcontinuum.core.knime.scheduler.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.knime.scheduler.exception.GlobalExceptionHandler
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowNotFoundException
import org.projectcontinuum.core.knime.scheduler.model.KnimeWorkflowResponse
import org.projectcontinuum.core.knime.scheduler.service.KnimeWorkflowService
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class KnimeWorkflowControllerTest {

  private val service: KnimeWorkflowService = mock()
  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(KnimeWorkflowController(service))
      .setControllerAdvice(GlobalExceptionHandler())
      .build()
  }

  @Test
  fun `get returns workflow metadata for the requesting user`() {
    val workflowId = UUID.randomUUID()
    val response = KnimeWorkflowResponse(
      workflowId = workflowId,
      fileName = "workflow.knwf",
      sizeBytes = 42L,
      contentType = "application/octet-stream",
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    whenever(service.get(workflowId, "alice")).thenReturn(response)

    mockMvc.perform(get("/api/v1/knime-workflows/$workflowId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.fileName").value("workflow.knwf"))
  }

  @Test
  fun `get returns 404 when the workflow belongs to a different user`() {
    val workflowId = UUID.randomUUID()
    whenever(service.get(workflowId, "bob")).thenThrow(KnimeWorkflowNotFoundException(workflowId))

    mockMvc.perform(get("/api/v1/knime-workflows/$workflowId").header("x-continuum-user-id", "bob"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `upload rejects a request missing the file part`() {
    mockMvc.perform(multipart("/api/v1/knime-workflows").header("x-continuum-user-id", "alice"))
      .andExpect(status().isBadRequest)
  }
}
