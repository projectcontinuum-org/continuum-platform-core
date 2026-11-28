package org.projectcontinuum.core.api.server.controller

import io.temporal.client.WorkflowClient
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
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
import org.projectcontinuum.core.commons.model.LoopNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
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
 * The graph checks on `POST /api/v1/workflow`, through the real [WorkflowService] and
 * [ApiExceptionHandler]. Only Temporal, the DB and the registry lookup are mocked. Each rejected case must leave no
 * `workflow_runs` row behind and never reach Temporal.
 */
@WebMvcTest(WorkflowController::class)
@Import(WorkflowService::class, TimerWorkflowStopScheduler::class, ApiExceptionHandler::class)
class WorkflowControllerGraphValidationTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var workflowClient: WorkflowClient

  @MockitoBean
  private lateinit var workflowRunRepository: WorkflowRunRepository

  @MockitoBean
  private lateinit var nodeExplorerService: NodeExplorerService

  private val port = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  @BeforeEach
  fun registerNodes() {
    whenever(nodeExplorerService.getNodeMetadata("org.test.Source")).thenReturn(
      ContinuumWorkflowModel.NodeData(description = "", title = "Source", nodeModel = "org.test.Source", outputs = mapOf("out" to port))
    )
    whenever(nodeExplorerService.getNodeMetadata("org.test.Step")).thenReturn(
      ContinuumWorkflowModel.NodeData(
        description = "", title = "Step", nodeModel = "org.test.Step",
        inputs = mapOf("in" to port), outputs = mapOf("out" to port),
        propertiesSchema = mapOf(
          "type" to "object",
          "properties" to mapOf("width" to mapOf("type" to "integer")),
          "required" to listOf("width")
        )
      )
    )
    whenever(nodeExplorerService.getNodeMetadata(LoopNode.NODE_MODEL)).thenReturn(LoopNodeDescriptor.nodeData)
  }

  private fun loopNode(id: String, conditionJson: String = "\"vars[\\\"hasMore\\\"] == true\"") = """
    {"id": "$id", "type": "Loop", "position": {"x": 0, "y": 0}, "width": 520, "height": 320, "selected": false,
     "data": {"title": "Loop", "description": "", "nodeModel": "${LoopNode.NODE_MODEL}", "collapsed": false,
              "properties": {"condition": $conditionJson, "maxIterations": 50, "onLimit": "fail",
                             "onNonBooleanResult": "fail", "check": "after"}}}
  """.trimIndent()

  /** s -> X1 inside loop L, with [condition] as the loop's condition. */
  private fun loopWorkflowWithCondition(condition: String) = workflow(
    nodes = listOf(
      node(id = "s", model = "org.test.Source"),
      loopNode(id = "L", conditionJson = jacksonObjectMapper().writeValueAsString(condition)),
      memberNode(id = "X1", parent = "L")
    ),
    edges = listOf(edge(source = "s", sourceHandle = "out", target = "X1", targetHandle = "in"))
  )

  private fun memberNode(id: String, parent: String) = """
    {"id": "$id", "type": "BaseNode", "parentNode": "$parent", "extent": "parent", "position": {"x": 40, "y": 60},
     "width": 1, "height": 1, "selected": false,
     "data": {"title": "t", "description": "", "nodeModel": "org.test.Step", "properties": {"width": 1}}}
  """.trimIndent()

  /** s -> X1 -> Y -> X2 with X1 and X2 inside loop L and Y outside: a path that leaves the loop and comes back. */
  private val loopReentryWorkflow
    get() = workflow(
      nodes = listOf(
        node(id = "s", model = "org.test.Source"),
        loopNode(id = "L"),
        memberNode(id = "X1", parent = "L"),
        node(id = "Y", model = "org.test.Step"),
        memberNode(id = "X2", parent = "L")
      ),
      edges = listOf(
        edge(source = "s", sourceHandle = "out", target = "X1", targetHandle = "in"),
        edge(source = "X1", sourceHandle = "out", target = "Y", targetHandle = "in"),
        edge(source = "Y", sourceHandle = "out", target = "X2", targetHandle = "in")
      )
    )

  private fun node(id: String, model: String, properties: String = """{"width": 1}""") = """
    {"id": "$id", "type": "default", "position": {"x": 0, "y": 0}, "width": 1, "height": 1, "selected": false,
     "data": {"title": "t", "description": "", "nodeModel": "$model", "properties": $properties}}
  """.trimIndent()

  private fun edge(source: String, sourceHandle: String, target: String, targetHandle: String) = """
    {"id": "$source-$sourceHandle-$target-$targetHandle", "source": "$source", "sourceHandle": "$sourceHandle",
     "target": "$target", "targetHandle": "$targetHandle"}
  """.trimIndent()

  private fun workflow(nodes: List<String>, edges: List<String>) =
    """{"id": "wf-1", "name": "/systest/wf.cwf", "nodes": [${nodes.joinToString(",")}], "edges": [${edges.joinToString(",")}]}"""

  private fun startWorkflow(body: String) =
    mockMvc.perform(post("/api/v1/workflow").contentType(MediaType.APPLICATION_JSON).content(body))

  private fun assertRejected(body: String, vararg expectedInDetail: String) {
    val result = startWorkflow(body).andExpect(status().isBadRequest)
    expectedInDetail.forEach { result.andExpect(jsonPath("$.detail", containsString(it))) }

    verifyNoInteractions(workflowClient)
    verify(workflowRunRepository, never()).save(any<WorkflowRunEntity>())
  }

  @Test
  fun `a cycle is 400`() = assertRejected(
    workflow(
      listOf(node(id = "a", model = "org.test.Step"), node(id = "b", model = "org.test.Step")),
      listOf(edge(source = "a", sourceHandle = "out", target = "b", targetHandle = "in"), edge(source = "b", sourceHandle = "out", target = "a", targetHandle = "in"))
    ),
    "cycle through nodes 'a', 'b'"
  )

  @Test
  fun `a duplicate edge is 400`() = assertRejected(
    workflow(
      listOf(node(id = "s", model = "org.test.Source"), node(id = "a", model = "org.test.Step")),
      listOf(edge(source = "s", sourceHandle = "out", target = "a", targetHandle = "in"), edge(source = "s", sourceHandle = "out", target = "a", targetHandle = "in"))
    ),
    "listed more than once"
  )

  @Test
  fun `an unknown node id is 400`() = assertRejected(
    workflow(listOf(node(id = "x", model = "org.test.Missing")), emptyList()),
    "Node 'x': no registered node with id 'org.test.Missing'"
  )

  @Test
  fun `an unknown port is 400`() = assertRejected(
    workflow(
      listOf(node(id = "s", model = "org.test.Source"), node(id = "a", model = "org.test.Step")),
      listOf(edge(source = "s", sourceHandle = "nope", target = "a", targetHandle = "in"), edge(source = "s", sourceHandle = "out", target = "a", targetHandle = "nope"))
    ),
    "has no output port 'nope'", "has no input port 'nope'"
  )

  @Test
  fun `two edges into one input are 400`() = assertRejected(
    workflow(
      listOf(node(id = "s1", model = "org.test.Source"), node(id = "s2", model = "org.test.Source"), node(id = "a", model = "org.test.Step")),
      listOf(edge(source = "s1", sourceHandle = "out", target = "a", targetHandle = "in"), edge(source = "s2", sourceHandle = "out", target = "a", targetHandle = "in"))
    ),
    "already fed by s1.out"
  )

  @Test
  fun `a missing required property is 400`() = assertRejected(
    workflow(listOf(node(id = "a", model = "org.test.Step", properties = "{}")), emptyList()),
    "Node 'a' properties", "width"
  )

  @Test
  fun `all errors are returned in one response`() {
    val body = workflow(
      listOf(node(id = "a", model = "org.test.Step", properties = "{}"), node(id = "x", model = "org.test.Missing")),
      listOf(edge(source = "a", sourceHandle = "out", target = "a", targetHandle = "in"))
    )

    startWorkflow(body)
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.errors", hasSize<Any>(3)))

    verifyNoInteractions(workflowClient)
    verify(workflowRunRepository, never()).save(any<WorkflowRunEntity>())
  }

  // --- POST /api/v1/workflow/validate: the same checks, no run ---

  private fun validateWorkflow(body: String, userId: String? = null) =
    mockMvc.perform(
      post("/api/v1/workflow/validate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .apply { userId?.let { header("x-continuum-user-id", it) } }
    )

  @Test
  fun `validate reports every error as a 200 and never touches Temporal or the database`() {
    val body = workflow(
      nodes = listOf(node(id = "a", model = "org.test.Step", properties = "{}"), node(id = "x", model = "org.test.Missing")),
      edges = listOf(edge(source = "a", sourceHandle = "out", target = "a", targetHandle = "in"))
    )

    validateWorkflow(body)
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.valid").value(false))
      .andExpect(jsonPath("$.nodeCount").value(2))
      .andExpect(jsonPath("$.edgeCount").value(1))
      .andExpect(jsonPath("$.errors", hasSize<Any>(3)))

    verifyNoInteractions(workflowClient)
    verifyNoInteractions(workflowRunRepository)
  }

  @Test
  fun `validate of a good workflow is valid and returns warnings without starting it`() {
    val body = workflow(nodes = listOf(node(id = "a", model = "org.test.Step")), edges = emptyList())

    validateWorkflow(body)
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.valid").value(true))
      .andExpect(jsonPath("$.errors", hasSize<Any>(0)))
      .andExpect(jsonPath("$.warnings[?(@.message =~ /.*input port 'in' has no incoming edge.*/)].node").value("a"))

    verifyNoInteractions(workflowClient)
    verifyNoInteractions(workflowRunRepository)
  }

  @Test
  fun `validate accepts the owner header and works without it`() {
    val body = workflow(nodes = listOf(node(id = "a", model = "org.test.Step")), edges = emptyList())

    validateWorkflow(body = body, userId = "alice").andExpect(status().isOk).andExpect(jsonPath("$.valid").value(true))
    validateWorkflow(body = body).andExpect(status().isOk).andExpect(jsonPath("$.valid").value(true))
  }

  @Test
  fun `a path that leaves a loop and comes back is 400 on start`() = assertRejected(
    loopReentryWorkflow,
    "The edges leave loop 'L' and come back into it through 'Y'."
  )

  @Test
  fun `a path that leaves a loop and comes back is valid false on validate`() {
    validateWorkflow(body = loopReentryWorkflow)
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.valid").value(false))
      .andExpect(jsonPath("$.errors", hasSize<Any>(1)))
      .andExpect(jsonPath("$.errors[0].node").value("L"))

    verifyNoInteractions(workflowClient)
    verifyNoInteractions(workflowRunRepository)
  }

  @Test
  fun `a loop with members connected through its members is valid`() {
    val body = workflow(
      nodes = listOf(node(id = "s", model = "org.test.Source"), loopNode(id = "L"), memberNode(id = "X1", parent = "L")),
      edges = listOf(edge(source = "s", sourceHandle = "out", target = "X1", targetHandle = "in"))
    )

    validateWorkflow(body = body)
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.valid").value(true))
  }

  @Test
  fun `a rejected loop condition is 400 on start and valid false on validate, with one error on the condition`() {
    listOf(
      "now() > 1" to "does not type-check",
      "vars[\"p\"] + 1" to "does not return true or false",
      "cel.bind(a, 1, cel.bind(a, a + a, a > 0))" to "binds a name that is already bound"
    ).forEach { (condition, reason) ->
      val body = loopWorkflowWithCondition(condition = condition)

      assertRejected(body, "Loop 'L': the condition $reason")
      validateWorkflow(body = body)
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.valid").value(false))
        .andExpect(jsonPath("$.errors", hasSize<Any>(1)))
        .andExpect(jsonPath("$.errors[0].node").value("L"))
        .andExpect(jsonPath("$.errors[0].property").value("condition"))
    }
  }

  @Test
  fun `a full CEL condition is valid on validate`() {
    validateWorkflow(body = loopWorkflowWithCondition(condition = "vars[\"page\"] < 10 && vars[\"hasMore\"]"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.valid").value(true))
  }

  @Test
  fun `validate and start agree on what is invalid`() {
    val body = workflow(
      nodes = listOf(node(id = "a", model = "org.test.Step"), node(id = "b", model = "org.test.Step")),
      edges = listOf(
        edge(source = "a", sourceHandle = "out", target = "b", targetHandle = "in"),
        edge(source = "b", sourceHandle = "out", target = "a", targetHandle = "in")
      )
    )

    validateWorkflow(body).andExpect(jsonPath("$.valid").value(false))
    startWorkflow(body).andExpect(status().isBadRequest)
  }
}
