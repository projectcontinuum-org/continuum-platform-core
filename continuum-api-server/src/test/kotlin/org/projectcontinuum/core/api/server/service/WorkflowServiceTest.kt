package org.projectcontinuum.core.api.server.service

import io.temporal.api.common.v1.Payload
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.api.workflowservice.v1.CountWorkflowExecutionsRequest
import io.temporal.api.workflowservice.v1.CountWorkflowExecutionsResponse
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsRequest
import io.temporal.api.workflowservice.v1.ListWorkflowExecutionsResponse
import io.temporal.api.workflowservice.v1.WorkflowServiceGrpc
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowNotFoundException
import io.temporal.client.WorkflowOptions
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.workflow.Functions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import com.google.protobuf.ByteString
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertSame
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

  @Test
  fun `cancel of another owner's workflow is not found and the workflow is never signalled`() {
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("run-1", "mallory")).thenReturn(false)

    assertThrows<WorkflowNotFoundException> { service.cancelWorkflow("mallory", "run-1", "because") }

    verify(workflowClient, never()).newWorkflowStub(eq(IContinuumWorkflow::class.java), any<String>())
  }

  @Test
  fun `terminate of another owner's workflow is not found and the workflow is never signalled`() {
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("run-1", "mallory")).thenReturn(false)

    assertThrows<WorkflowNotFoundException> { service.terminateWorkflow("mallory", "run-1", "because") }

    verify(workflowClient, never()).newWorkflowStub(eq(IContinuumWorkflow::class.java), any<String>())
  }

  @Test
  fun `cancel and terminate of an unknown workflow id are not found, not silently accepted`() {
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("no-such-run", "alice")).thenReturn(false)

    assertThrows<WorkflowNotFoundException> { service.cancelWorkflow("alice", "no-such-run", null) }
    assertThrows<WorkflowNotFoundException> { service.terminateWorkflow("alice", "no-such-run", null) }
  }

  @Test
  fun `owner can cancel their own workflow and the cancelling signal is sent`() {
    val stub: IContinuumWorkflow = mock()
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("run-1", "alice")).thenReturn(true)
    whenever(workflowClient.newWorkflowStub(eq(IContinuumWorkflow::class.java), eq("run-1"))).thenReturn(stub)

    service.cancelWorkflow("alice", "run-1", "no longer needed")

    verify(stub).notifyCancelling("no longer needed")
  }

  @Test
  fun `owner can terminate their own workflow and the terminating signal is sent`() {
    val stub: IContinuumWorkflow = mock()
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("run-1", "alice")).thenReturn(true)
    whenever(workflowClient.newWorkflowStub(eq(IContinuumWorkflow::class.java), eq("run-1"))).thenReturn(stub)

    service.terminateWorkflow("alice", "run-1", "stuck")

    verify(stub).notifyTerminating("stuck")
  }

  @Test
  fun `active workflows are scoped to the caller's owner filter`() {
    whenever(workflowRunRepository.findWorkflowIdsByOwnedBy("alice")).thenReturn(listOf("run-1", "run-2"))

    service.getActiveWorkflows("alice")

    assertEquals(
      listOf(
        "(WorkflowId IN ('run-1', 'run-2')) AND " +
          "(WorkflowType='IContinuumWorkflow' && ExecutionStatus='WORKFLOW_EXECUTION_STATUS_RUNNING')"
      ),
      sentListQueries()
    )
  }

  @Test
  fun `caller with no runs sees no active workflows and Temporal is not queried`() {
    whenever(workflowRunRepository.findWorkflowIdsByOwnedBy("mallory")).thenReturn(emptyList())

    assertTrue(service.getActiveWorkflows("mallory").isEmpty())
    verify(blockingStub, never()).listWorkflowExecutions(any())
  }

  // --- startWorkflow: the Temporal run id is recorded right after the start ---

  private val emptyWorkflow = ContinuumWorkflowModel(id = "id", name = "/systest/wf.cwf")

  private fun execution(workflowId: String, runId: String?): WorkflowExecution =
    WorkflowExecution.newBuilder().setWorkflowId(workflowId).apply { runId?.let { setRunId(it) } }.build()

  /** Runs [block] with the static `WorkflowClient.start(...)` replaced by [onStart]. */
  private fun <T> withTemporalStart(onStart: () -> WorkflowExecution, block: () -> T): T {
    whenever(workflowClient.newWorkflowStub(eq(IContinuumWorkflow::class.java), any<WorkflowOptions>()))
      .thenReturn(mock<IContinuumWorkflow>())
    Mockito.mockStatic(WorkflowClient::class.java).use { static ->
      static.`when`<WorkflowExecution> {
        WorkflowClient.start(any<Functions.Func1<ContinuumWorkflowModel, Map<String, Map<String, PortData>>>>(), any())
      }.thenAnswer { onStart() }
      return block()
    }
  }

  @Test
  fun `start records the Temporal run id on the run row`() {
    val workflowId = withTemporalStart({ execution("ignored", "run-123") }) {
      service.startWorkflow(emptyWorkflow, "alice")
    }

    val saved = argumentCaptor<WorkflowRunEntity>()
    verify(workflowRunRepository).save(saved.capture())
    assertEquals("alice", saved.firstValue.ownedBy)
    assertEquals("PENDING", saved.firstValue.status)
    // The run id is written with a targeted update, never with a second save() that would overwrite newer data.
    verify(workflowRunRepository).updateRunId(eq(saved.firstValue.workflowId), eq("run-123"))
    verify(workflowRunRepository, times(1)).save(any<WorkflowRunEntity>())
    assertEquals("ignored", workflowId)
  }

  @Test
  fun `the run row exists before the workflow is started`() {
    val order = mutableListOf<String>()
    whenever(workflowRunRepository.save(any<WorkflowRunEntity>())).thenAnswer { order += "save"; it.arguments[0] }

    withTemporalStart({ order += "start"; execution("wf", "run-1") }) { service.startWorkflow(emptyWorkflow, "alice") }

    assertEquals(listOf("save", "start"), order)
  }

  @Test
  fun `a failed start removes the orphaned run row and rethrows`() {
    val failure = RuntimeException("temporal is down")

    val thrown = assertThrows<RuntimeException> {
      withTemporalStart({ throw failure }) { service.startWorkflow(emptyWorkflow, "alice") }
    }

    assertSame(failure, thrown)
    val saved = argumentCaptor<WorkflowRunEntity>()
    verify(workflowRunRepository).save(saved.capture())
    verify(workflowRunRepository).deleteById(saved.firstValue.workflowId)
    verify(workflowRunRepository, never()).updateRunId(any(), any())
  }

  @Test
  fun `failing to record the run id does not fail a workflow that is already running`() {
    whenever(workflowRunRepository.updateRunId(any(), any())).thenThrow(RuntimeException("db down"))

    val workflowId = withTemporalStart({ execution("wf-1", "run-1") }) { service.startWorkflow(emptyWorkflow, "alice") }

    assertEquals("wf-1", workflowId)
  }

  @Test
  fun `a start with no run id leaves it for the message bridge`() {
    withTemporalStart({ execution("wf-1", null) }) { service.startWorkflow(emptyWorkflow, "alice") }

    verify(workflowRunRepository, never()).updateRunId(any(), any())
  }
}
