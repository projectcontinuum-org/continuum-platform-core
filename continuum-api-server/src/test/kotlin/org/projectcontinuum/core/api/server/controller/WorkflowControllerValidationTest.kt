package org.projectcontinuum.core.api.server.controller

import io.temporal.client.WorkflowClient
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.projectcontinuum.core.api.server.exception.ApiExceptionHandler
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import org.projectcontinuum.core.api.server.service.WorkflowService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.hamcrest.Matchers.containsString

/** `POST /api/v1/workflow` through the real [WorkflowService] and [ApiExceptionHandler]; only Temporal and the DB are mocked. */
@WebMvcTest(WorkflowController::class)
@Import(WorkflowService::class, ApiExceptionHandler::class)
class WorkflowControllerValidationTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var workflowClient: WorkflowClient

  @MockitoBean
  private lateinit var workflowRunRepository: WorkflowRunRepository

  private fun workflowJson(width: String) = """
    {
      "id": "wf-1",
      "name": "/systest/wf.cwf",
      "nodes": [{
        "id": "n1", "type": "default", "position": {"x": 0, "y": 0}, "width": 1, "height": 1, "selected": false,
        "data": {
          "title": "Typed", "description": "", "nodeModel": "org.test.Typed",
          "properties": {"width": $width},
          "propertiesSchema": {"type": "object", "properties": {"width": {"type": "integer"}}, "required": ["width"]}
        }
      }],
      "edges": []
    }
  """.trimIndent()

  @Test
  fun `start with invalid node properties is 400 and nothing reaches Temporal or the database`() {
    mockMvc.perform(post("/api/v1/workflow").contentType(MediaType.APPLICATION_JSON).content(workflowJson("\"wide\"")))
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.detail", containsString("node 'n1'")))
      .andExpect(jsonPath("$.detail", containsString("/width")))

    verifyNoInteractions(workflowClient)
    verify(workflowRunRepository, never()).save(any<WorkflowRunEntity>())
  }
}
