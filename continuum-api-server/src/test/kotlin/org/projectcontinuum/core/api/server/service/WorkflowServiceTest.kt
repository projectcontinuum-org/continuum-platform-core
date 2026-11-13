package org.projectcontinuum.core.api.server.service

import io.temporal.api.common.v1.Payload
import io.temporal.api.workflowservice.v1.CountWorkflowExecutionsRequest
import io.temporal.api.workflowservice.v1.CountWorkflowExecutionsResponse
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsResponse
import io.temporal.api.workflowservice.v1.WorkflowServiceGrpc
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowNotFoundException
import io.temporal.serviceclient.WorkflowServiceStubs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import com.google.protobuf.ByteString
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowServiceTest {

  private lateinit var workflowClient: WorkflowClient
  private lateinit var blockingStub: WorkflowServiceGrpc.WorkflowServiceBlockingStub
  private lateinit var workflowRunRepository: WorkflowRunRepository
  private lateinit var service: WorkflowService

  @BeforeEach
  fun setUp() {
    workflowClient = mock()
    blockingStub = mock()
    val stubs: WorkflowServiceStubs = mock()
    whenever(workflowClient.options).thenReturn(WorkflowClientOptions.newBuilder().setNamespace("default").build())
    whenever(workflowClient.workflowServiceStubs).thenReturn(stubs)
    whenever(stubs.blockingStub()).thenReturn(blockingStub)
    whenever(blockingStub.listWorkflowExecutions(any())).thenReturn(ListWorkflowExecutionsResponse.getDefaultInstance())
    workflowRunRepository = mock()
    service = WorkflowService(workflowClient, workflowRunRepository, JsonMapper.builder().build())
  }

  private fun sentListQueries(times: Int = 1): List<String> {
    val captor = argumentCaptor<ListWorkflowExecutionsRequest>()
    verify(blockingStub, times(times)).listWorkflowExecutions(captor.capture())
    return captor.allValues.map { it.query }
  }

  @Test
  fun `list query is AND-combined with the caller's owner filter`() {
    whenever(workflowRunRepository.findWorkflowIdsByOwnedBy("alice")).thenReturn(listOf("run-1", "run-2"))

    service.listAllWorkflow("alice", "ExecutionStatus = 'Running'")

    assertEquals(
      listOf("(WorkflowId IN ('run-1', 'run-2')) AND (ExecutionStatus = 'Running')"),
      sentListQueries()
    )
  }

  @Test
  fun `blank list query is scoped to the caller only`() {
    whenever(workflowRunRepository.findWorkflowIdsByOwnedBy("alice")).thenReturn(listOf("run-1"))

    service.listAllWorkflow("alice", "")

    assertEquals(listOf("(WorkflowId IN ('run-1'))"), sentListQueries())
  }

  @Test
  fun `caller with no runs gets nothing and Temporal is not queried`() {
    whenever(workflowRunRepository.findWorkflowIdsByOwnedBy("mallory")).thenReturn(emptyList())

    val result = service.listAllWorkflow("mallory", "")

    assertTrue(result.isEmpty())
    verify(blockingStub, never()).listWorkflowExecutions(any())
  }

  @Test
  fun `a quote in a stored workflow id is escaped in the owner filter`() {
    whenever(workflowRunRepository.findWorkflowIdsByOwnedBy("alice")).thenReturn(listOf("x') OR ('1'='1"))

    service.listAllWorkflow("alice", "")

    assertEquals(listOf("(WorkflowId IN ('x\\') OR (\\'1\\'=\\'1'))"), sentListQueries())
  }

  @Test
  fun `owner filter is chunked for callers with many runs`() {
    whenever(workflowRunRepository.findWorkflowIdsByOwnedBy("alice")).thenReturn((1..501).map { "run-$it" })

    service.listAllWorkflow("alice", "")

    val queries = sentListQueries(times = 2)
    assertTrue(queries[1] == "(WorkflowId IN ('run-501'))", queries[1])
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "'",
      "'); SELECT 1; --",
      ") OR (WorkflowId != ''",
      "ExecutionStatus = 'Running' --",
      "ExecutionStatus = 'Running'\nOR 1=1",
    ]
  )
  fun `hostile list and count queries are rejected before reaching Temporal`(hostile: String) {
    whenever(workflowRunRepository.findWorkflowIdsByOwnedBy("alice")).thenReturn(listOf("run-1"))

    assertThrows<InvalidRequestException> { service.listAllWorkflow("alice", hostile) }
    assertThrows<InvalidRequestException> { service.countWorkflow("alice", hostile) }
    assertThrows<InvalidRequestException> { service.getWorkflowTree("alice", "/", hostile) }
    verify(blockingStub, never()).listWorkflowExecutions(any())
    verify(blockingStub, never()).countWorkflowExecutions(any())
  }

  @Test
  fun `count is scoped and sums groups across owner chunks`() {
    whenever(workflowRunRepository.findWorkflowIdsByOwnedBy("alice")).thenReturn((1..501).map { "run-$it" })
    val running = Payload.newBuilder().setData(ByteString.copyFromUtf8("\"Running\"")).build()
    whenever(blockingStub.countWorkflowExecutions(any())).thenReturn(
      CountWorkflowExecutionsResponse.newBuilder()
        .setCount(2)
        .addGroups(CountWorkflowExecutionsResponse.AggregationGroup.newBuilder().addGroupValues(running).setCount(2))
        .build()
    )

    val response = service.countWorkflow("alice", "GROUP BY ExecutionStatus")

    val captor = argumentCaptor<CountWorkflowExecutionsRequest>()
    verify(blockingStub, times(2)).countWorkflowExecutions(captor.capture())
    assertTrue(captor.allValues.all { it.query.startsWith("(WorkflowId IN (") && it.query.endsWith(") GROUP BY ExecutionStatus") })
    assertEquals(4, response.count)
    assertEquals(1, response.groupsCount)
    assertEquals(4, response.getGroups(0).count)
  }

  @Test
  fun `status by id escapes the workflow id`() {
    val hostileId = "x' OR WorkflowId != '"
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy(hostileId, "alice")).thenReturn(true)

    assertThrows<RuntimeException> { service.getWorkflowStatusById("alice", hostileId) }

    assertEquals(
      listOf("WorkflowType = 'IContinuumWorkflow' AND WorkflowId = 'x\\' OR WorkflowId != \\''"),
      sentListQueries()
    )
  }

  @Test
  fun `status by id of another owner's workflow is not found and Temporal is not queried`() {
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("run-1", "mallory")).thenReturn(false)

    assertThrows<WorkflowNotFoundException> { service.getWorkflowStatusById("mallory", "run-1") }
    verify(blockingStub, never()).listWorkflowExecutions(any())
  }
}
