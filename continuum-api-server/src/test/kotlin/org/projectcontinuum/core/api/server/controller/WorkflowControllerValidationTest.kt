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
import org.projectcontinuum.core.api.server.service.TimerWorkflowStopScheduler
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
 * `POST /api/v1/workflow` through the real [WorkflowService] and [ApiExceptionHandler];
 * only Temporal, the DB and the node registry lookup are mocked.
 */
@WebMvcTest(WorkflowController::class)
@Import(WorkflowService::class, TimerWorkflowStopScheduler::class, ApiExceptionHandler::class)
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
      .andExpect(jsonPath("$.detail", containsString("Node 'n1'")))
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

  private fun workflowNamed(name: String) = workflowJson("10", "{}").replace("\"/systest/wf.cwf\"", "\"$name\"")

  @Test
  fun `start with a workflow name that is not a URI is 400 and nothing reaches Temporal or the database`() {
    mockMvc.perform(post("/api/v1/workflow").contentType(MediaType.APPLICATION_JSON).content(workflowNamed("My Workflow.cwf")))
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.title").value("Invalid workflow"))
      .andExpect(jsonPath("$.errors[0].message", containsString("is not a valid URI path")))

    verifyNoInteractions(workflowClient)
    verify(workflowRunRepository, never()).save(any<WorkflowRunEntity>())
  }

  @Test
  fun `validate reports a workflow name that is not a URI as an error`() {
    mockMvc.perform(post("/api/v1/workflow/validate").contentType(MediaType.APPLICATION_JSON).content(workflowNamed("My Workflow.cwf")))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.valid").value(false))
      .andExpect(jsonPath("$.errors[0].message", containsString("is not a valid URI path")))
  }

  @Test
  fun `a percent-encoded workflow name is accepted by validate`() {
    mockMvc.perform(post("/api/v1/workflow/validate").contentType(MediaType.APPLICATION_JSON).content(workflowNamed("examples/My%20Workflow.cwf")))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.valid").value(true))
  }
}
