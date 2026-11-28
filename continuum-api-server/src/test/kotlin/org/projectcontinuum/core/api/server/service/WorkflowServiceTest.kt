package org.projectcontinuum.core.api.server.service

import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowNotFoundException
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import io.temporal.workflow.Functions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.api.server.exception.WorkflowValidationException
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkflowServiceTest {

  private lateinit var workflowClient: WorkflowClient
  private lateinit var workflowRunRepository: WorkflowRunRepository
  private lateinit var nodeExplorerService: NodeExplorerService
  private lateinit var service: WorkflowService

  @BeforeEach
  fun setUp() {
    workflowClient = mock()
    workflowRunRepository = mock()
    nodeExplorerService = mock()
    service = WorkflowService(
      workflowClient = workflowClient,
      workflowRunRepository = workflowRunRepository,
      nodeExplorerService = nodeExplorerService,
      stopScheduler = immediateStopScheduler
    )
  }

  /** Runs the delayed hard stop at once, on the calling thread, and remembers the grace period it was given. */
  private val scheduledStopDelays = mutableListOf<Long>()
  private val immediateStopScheduler = WorkflowStopScheduler { delayMillis, stop ->
    scheduledStopDelays += delayMillis
    stop()
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

  // --- terminate: the run record reaches TERMINATED even when the run cannot handle the signal ---

  /**
   * An owned, running workflow whose signal is accepted by Temporal but never handled, like a run that keeps
   * retrying a workflow task: nothing in the run publishes a final status, so only the API server can record it.
   */
  private fun stuckOwnedRun(workflowId: String, ownedBy: String): WorkflowStub {
    val typedStub: IContinuumWorkflow = mock()
    val untypedStub: WorkflowStub = mock()
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy(workflowId, ownedBy)).thenReturn(true)
    whenever(workflowClient.newWorkflowStub(eq(IContinuumWorkflow::class.java), eq(workflowId))).thenReturn(typedStub)
    whenever(workflowClient.newUntypedWorkflowStub(workflowId)).thenReturn(untypedStub)
    return untypedStub
  }

  @Test
  fun `terminating a run that never handles the signal records it as terminated once Temporal has stopped it`() {
    val untypedStub = stuckOwnedRun(workflowId = "run-1", ownedBy = "alice")
    val before = Instant.now()

    service.terminateWorkflow(ownedBy = "alice", workflowId = "run-1", reason = "stuck")

    assertEquals(listOf(WorkflowService.STOP_SIGNAL_GRACE_PERIOD_MS), scheduledStopDelays)
    val terminatedAt = argumentCaptor<Instant>()
    val order = inOrder(untypedStub, workflowRunRepository)
    order.verify(untypedStub).terminate("stuck")
    order.verify(workflowRunRepository).markTerminated(
      workflowId = eq("run-1"),
      ownedBy = eq("alice"),
      terminatedAt = terminatedAt.capture()
    )
    assertTrue(!terminatedAt.firstValue.isBefore(before), "the end time is when the terminate was confirmed")
  }

  @Test
  fun `a run that ended before the hard stop keeps the status it ended with`() {
    val untypedStub = stuckOwnedRun(workflowId = "run-1", ownedBy = "alice")
    val alreadyEnded = WorkflowNotFoundException(WorkflowExecution.newBuilder().setWorkflowId("run-1").build(), null, null)
    doThrow(alreadyEnded).whenever(untypedStub).terminate("Terminated via API")

    service.terminateWorkflow(ownedBy = "alice", workflowId = "run-1", reason = null)

    verify(untypedStub).terminate("Terminated via API")
    verify(workflowRunRepository, never()).markTerminated(workflowId = any(), ownedBy = any(), terminatedAt = any())
  }

  @Test
  fun `terminate of another owner's workflow never touches the run record`() {
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("run-1", "mallory")).thenReturn(false)

    assertThrows<WorkflowNotFoundException> {
      service.terminateWorkflow(ownedBy = "mallory", workflowId = "run-1", reason = "because")
    }

    assertTrue(scheduledStopDelays.isEmpty(), "no hard stop may be scheduled for a run the caller does not own")
    verify(workflowRunRepository, never()).markTerminated(workflowId = any(), ownedBy = any(), terminatedAt = any())
  }

  @Test
  fun `cancel still only requests the Temporal cancellation and leaves the status to the run`() {
    val untypedStub = stuckOwnedRun(workflowId = "run-1", ownedBy = "alice")

    service.cancelWorkflow(ownedBy = "alice", workflowId = "run-1", reason = "no longer needed")

    verify(untypedStub).cancel()
    verify(workflowRunRepository, never()).markTerminated(workflowId = any(), ownedBy = any(), terminatedAt = any())
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
      service.startWorkflow(emptyWorkflow, "alice").workflowId
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

    val workflowId = withTemporalStart({ execution("wf-1", "run-1") }) { service.startWorkflow(emptyWorkflow, "alice").workflowId }

    assertEquals("wf-1", workflowId)
  }

  @Test
  fun `a start with no run id leaves it for the message bridge`() {
    withTemporalStart({ execution("wf-1", null) }) { service.startWorkflow(emptyWorkflow, "alice") }

    verify(workflowRunRepository, never()).updateRunId(any(), any())
  }

  // --- startWorkflow: node properties are validated against the registry before anything is stored or started ---

  private val registeredSchema = mapOf(
    "type" to "object",
    "properties" to mapOf("width" to mapOf("type" to "integer")),
    "required" to listOf("width")
  )

  private fun registerTypedNode() {
    whenever(nodeExplorerService.getNodeMetadata("org.test.Typed")).thenReturn(
      ContinuumWorkflowModel.NodeData(description = "", title = "Typed", nodeModel = "org.test.Typed", propertiesSchema = registeredSchema)
    )
  }

  /** A node as a client sends it; [sentSchema] is what the client claims the schema is. */
  private fun nodeWith(
    id: String,
    properties: Map<String, Any>,
    nodeModel: String = "org.test.Typed",
    sentSchema: Map<String, Any> = registeredSchema
  ) = ContinuumWorkflowModel.Node(
    id = id,
    type = "default",
    position = ContinuumWorkflowModel.Position(0.0, 0.0),
    width = 1,
    height = 1,
    selected = false,
    data = ContinuumWorkflowModel.NodeData(
      description = "",
      title = "Typed",
      nodeModel = nodeModel,
      properties = properties,
      propertiesSchema = sentSchema
    )
  )

  private fun assertRejectedBeforeStart(workflow: ContinuumWorkflowModel): InvalidRequestException {
    val e = assertThrows<InvalidRequestException> {
      withTemporalStart({ error("must not start") }) { service.startWorkflow(workflow, "alice") }
    }
    verify(workflowRunRepository, never()).save(any<WorkflowRunEntity>())
    return e
  }

  @Test
  fun `start with invalid node properties is an InvalidRequestException and nothing is saved or started`() {
    registerTypedNode()
    val workflow = emptyWorkflow.copy(nodes = listOf(nodeWith("ok", mapOf("width" to 1)), nodeWith("bad", mapOf("width" to "wide"))))

    val e = assertRejectedBeforeStart(workflow)

    assertTrue(e.message!!.contains("Node 'bad'") && e.message!!.contains("/width"), e.message)
    assertTrue(!e.message!!.contains("Node 'ok'"), e.message)
  }

  @Test
  fun `a client-sent empty schema does not bypass validation`() {
    registerTypedNode()
    val workflow = emptyWorkflow.copy(nodes = listOf(nodeWith("bad", mapOf("width" to "wide"), sentSchema = emptyMap())))

    val e = assertRejectedBeforeStart(workflow)

    assertTrue(e.message!!.contains("Node 'bad'") && e.message!!.contains("/width"), e.message)
  }

  @Test
  fun `a client-sent schema cannot loosen the registered one`() {
    registerTypedNode()
    val loose = mapOf("type" to "object", "properties" to mapOf("width" to mapOf("type" to "string")))
    val workflow = emptyWorkflow.copy(nodes = listOf(nodeWith("bad", mapOf("width" to "wide"), sentSchema = loose)))

    assertRejectedBeforeStart(workflow)
  }

  @Test
  fun `a node that is not in the registry is rejected`() {
    val workflow = emptyWorkflow.copy(nodes = listOf(nodeWith("x", emptyMap(), nodeModel = "org.evil.Unknown", sentSchema = emptyMap())))

    val e = assertRejectedBeforeStart(workflow)

    assertTrue(e.message!!.contains("Node 'x'") && e.message!!.contains("no registered node"), e.message)
  }

  @Test
  fun `start with valid node properties proceeds`() {
    registerTypedNode()
    val workflow = emptyWorkflow.copy(nodes = listOf(nodeWith("ok", mapOf("width" to 1)), nodeWith("ok2", mapOf("width" to 2))))

    withTemporalStart({ execution("wf-1", "run-1") }) { service.startWorkflow(workflow, "alice") }

    verify(workflowRunRepository).save(any<WorkflowRunEntity>())
    // Each node type is looked up once, however many times it appears.
    verify(nodeExplorerService, times(1)).getNodeMetadata("org.test.Typed")
  }

  // --- startWorkflow: graph checks and warnings ---

  private val portType = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  private fun registerStepNode() {
    whenever(nodeExplorerService.getNodeMetadata("org.test.Step")).thenReturn(
      ContinuumWorkflowModel.NodeData(
        description = "", title = "Step", nodeModel = "org.test.Step",
        inputs = mapOf("in" to portType), outputs = mapOf("out" to portType)
      )
    )
  }

  private fun stepNode(id: String) =
    nodeWith(id = id, properties = emptyMap(), nodeModel = "org.test.Step", sentSchema = emptyMap())

  @Test
  fun `a node with an unconnected input still starts, and the warning is returned and logged not thrown`() {
    registerStepNode()
    val workflow = emptyWorkflow.copy(nodes = listOf(stepNode("a")))

    val response = withTemporalStart({ execution("wf-1", "run-1") }) { service.startWorkflow(workflow, "alice") }

    assertEquals("wf-1", response.workflowId)
    assertTrue(response.warnings.any { it.message.contains("input port 'in' has no incoming edge") }, response.warnings.toString())
    verify(workflowRunRepository).save(any<WorkflowRunEntity>())
  }

  @Test
  fun `a cyclic workflow is rejected before anything is saved or started`() {
    registerStepNode()
    fun edge(source: String, target: String) = ContinuumWorkflowModel.Edge(
      id = "$source-$target",
      source = source,
      target = target,
      sourceHandle = "out",
      targetHandle = "in"
    )
    val workflow = emptyWorkflow.copy(
      nodes = listOf(stepNode("a"), stepNode("b")),
      edges = listOf(edge(source = "a", target = "b"), edge(source = "b", target = "a"))
    )

    val e = assertThrows<WorkflowValidationException> {
      withTemporalStart({ error("must not start") }) { service.startWorkflow(workflow, "alice") }
    }

    assertTrue(e.errors.single().message.contains("cycle"), e.message)
    verify(workflowRunRepository, never()).save(any<WorkflowRunEntity>())
  }

  @Test
  fun `a client schema that differs from the registered one is a warning and the registered schema decides`() {
    registerTypedNode()
    val loose = mapOf("type" to "object", "properties" to mapOf("width" to mapOf("type" to "integer")))
    val workflow = emptyWorkflow.copy(nodes = listOf(nodeWith(id = "ok", properties = mapOf("width" to 1), sentSchema = loose)))

    val response = withTemporalStart({ execution("wf-1", "run-1") }) { service.startWorkflow(workflow, "alice") }

    assertTrue(response.warnings.any { it.node == "ok" && it.message.contains("propertiesSchema") }, response.warnings.toString())
  }
}
