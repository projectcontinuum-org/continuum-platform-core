package org.projectcontinuum.core.api.server.controller

import io.temporal.client.WorkflowClient
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.projectcontinuum.core.api.server.exception.ApiExceptionHandler
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.api.server.service.WorkflowPropertiesValidator
import org.projectcontinuum.core.api.server.service.WorkflowService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * `POST /api/v1/workflow` through the real [WorkflowService], [WorkflowPropertiesValidator] and [ApiExceptionHandler];
 * only Temporal, the DB and the node registry lookup are mocked.
 */
@WebMvcTest(WorkflowController::class)
@Import(WorkflowService::class, WorkflowPropertiesValidator::class, ApiExceptionHandler::class)
class WorkflowControllerValidationTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var workflowClient: WorkflowClient

  @MockitoBean
  private lateinit var workflowRunRepository: WorkflowRunRepository

  @MockitoBean
  private lateinit var nodeExplorerService: NodeExplorerService

  @BeforeEach
  fun registerNode() {
    whenever(nodeExplorerService.getNodeMetadata("org.test.Typed")).thenReturn(
      ContinuumWorkflowModel.NodeData(
        description = "", title = "Typed", nodeModel = "org.test.Typed",
        propertiesSchema = mapOf(
          "type" to "object",
          "properties" to mapOf("width" to mapOf("type" to "integer")),
          "required" to listOf("width")
        )
      )
    )
  }

  private fun workflowJson(width: String, sentSchema: String) = """
    {
      "id": "wf-1",
      "name": "/systest/wf.cwf",
      "nodes": [{
        "id": "n1", "type": "default", "position": {"x": 0, "y": 0}, "width": 1, "height": 1, "selected": false,
        "data": {
          "title": "Typed", "description": "", "nodeModel": "org.test.Typed",
          "properties": {"width": $width},
          "propertiesSchema": $sentSchema
        }
      }],
      "edges": []
    }
  """.trimIndent()

  private fun assertRejected(body: String) {
    mockMvc.perform(post("/api/v1/workflow").contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.detail", containsString("node 'n1'")))
      .andExpect(jsonPath("$.detail", containsString("/width")))

    verifyNoInteractions(workflowClient)
    verify(workflowRunRepository, never()).save(any<WorkflowRunEntity>())
  }

  @Test
  fun `start with invalid node properties is 400 and nothing reaches Temporal or the database`() {
    assertRejected(
      workflowJson("\"wide\"", """{"type": "object", "properties": {"width": {"type": "integer"}}, "required": ["width"]}""")
    )
  }

  @Test
  fun `start with invalid node properties and an empty client schema is still 400`() {
    assertRejected(workflowJson("\"wide\"", "{}"))
  }
}
