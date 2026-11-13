package org.projectcontinuum.core.api.server.controller

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.api.server.exception.ResourceNotFoundException
import org.projectcontinuum.core.api.server.service.NodeOutputService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(NodeOutputController::class)
class NodeOutputControllerTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var nodeOutputService: NodeOutputService

  @Test
  fun `InvalidRequestException from validation maps to 400 problem detail`() {
    // Spring strips ";..." matrix content from path segments, so match any id here; the repository tests cover
    // the validation of each hostile value.
    whenever(nodeOutputService.getOutput(any(), any(), any(), any(), any(), any()))
      .thenThrow(InvalidRequestException("Invalid workflowId"))

    mockMvc.perform(get("/api/v1/data/{workflowId}/nodes/{nodeId}/outputs/{outputId}", "x'); SELECT 1; --", "1", "output"))
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.status").value(400))
      .andExpect(jsonPath("$.detail").value("Invalid workflowId"))
  }

  @Test
  fun `the user header is passed to the service as the owner`() {
    whenever(nodeOutputService.getOutput(any(), any(), any(), any(), any(), any()))
      .thenThrow(ResourceNotFoundException("Workflow run not found"))

    mockMvc.perform(
      get("/api/v1/data/run-1/nodes/node-1/outputs/output").header("x-continuum-user-id", "alice")
    )

    verify(nodeOutputService).getOutput(eq("alice"), eq("run-1"), eq("node-1"), eq("output"), eq(0), eq(10))
  }

  @Test
  fun `missing user header falls back to anonymous`() {
    whenever(nodeOutputService.getOutput(any(), any(), any(), any(), any(), any()))
      .thenThrow(ResourceNotFoundException("Workflow run not found"))

    mockMvc.perform(get("/api/v1/data/run-1/nodes/node-1/outputs/output"))

    verify(nodeOutputService).getOutput(eq("anonymous"), eq("run-1"), eq("node-1"), eq("output"), eq(0), eq(10))
  }

  @Test
  fun `a run the caller does not own is a 404 problem detail that does not reveal why`() {
    whenever(nodeOutputService.getOutput(any(), any(), any(), any(), any(), any()))
      .thenThrow(ResourceNotFoundException("Workflow run not found"))

    mockMvc.perform(
      get("/api/v1/data/run-1/nodes/node-1/outputs/output").header("x-continuum-user-id", "mallory")
    )
      .andExpect(status().isNotFound)
      .andExpect(jsonPath("$.status").value(404))
      .andExpect(jsonPath("$.detail").value("Workflow run not found"))
  }
}
