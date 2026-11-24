package org.projectcontinuum.core.orchestration.workflow

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.api.enums.v1.IndexedValueType
import io.temporal.client.*
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.failure.ApplicationFailure
import io.temporal.failure.CanceledFailure
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.model.WorkflowUpdateEvent
import org.projectcontinuum.core.commons.protocol.progress.ContinuumNodeActivitySignal
import org.projectcontinuum.core.commons.protocol.progress.NodeProgress
import org.projectcontinuum.core.commons.utils.FlowVariables
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import org.projectcontinuum.core.orchestration.utils.StatusHelper
import org.projectcontinuum.core.orchestration.workflow.fixtures.FakeContinuumNodeActivity
import org.projectcontinuum.core.orchestration.workflow.fixtures.FakeInitializeActivity
import org.projectcontinuum.core.orchestration.workflow.fixtures.WorkflowFixtures
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.messaging.Message
import java.io.Closeable
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure Temporal unit tests for [ContinuumWorkflow] using [TestWorkflowEnvironment] — no Spring
 * context, no Kafka. Workflow definitions are loaded from classpath `.cwf.json` resources via
 * [WorkflowFixtures]; node execution is driven by [FakeContinuumNodeActivity] /
 * [FakeInitializeActivity] instead of real node business logic.
 */
class ContinuumWorkflowTest {

  private val activityTaskQueue = "TEST_FAKE_CONTINUUM_NODE_ACTIVITY_TASK_QUEUE"

  @AfterEach
  fun resetStatusPublisher() {
    // captureStatusEvents() installs a mock in the static StatusHelper; don't leak it into other tests
    StatusHelper.reset()
  }

  private class Env(
    val testEnv: TestWorkflowEnvironment,
    val client: WorkflowClient,
    val fakeNodeActivity: FakeContinuumNodeActivity
  ) : Closeable {
    override fun close() = testEnv.close()
  }

  private fun newEnv(dropOneNode: Boolean = false, initializeGate: CountDownLatch? = null): Env {
    val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper()
      .apply { registerModule(KotlinModule.Builder().build()) }
    val dataConverter = DefaultDataConverter.newDefaultInstance()
      .withPayloadConverterOverrides(JacksonJsonPayloadConverter(mapper))

    val testEnv = TestWorkflowEnvironment.newInstance(
      TestEnvironmentOptions.newBuilder()
        .setWorkflowClientOptions(WorkflowClientOptions.newBuilder().setDataConverter(dataConverter).build())
        .registerSearchAttribute("Continuum:ExecutionStatus", IndexedValueType.INDEXED_VALUE_TYPE_INT)
        .registerSearchAttribute("Continuum:WorkflowFileName", IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD)
        .build()
    )

    testEnv.newWorker(TaskQueues.WORKFLOW_TASK_QUEUE)
      .registerWorkflowImplementationTypes(ContinuumWorkflow::class.java)

    val fakeNodeActivity = FakeContinuumNodeActivity()
    testEnv.newWorker(activityTaskQueue).registerActivitiesImplementations(fakeNodeActivity)

    testEnv.newWorker(TaskQueues.ACTIVITY_TASK_QUEUE_INITIALIZE)
      .registerActivitiesImplementations(FakeInitializeActivity(activityTaskQueue, dropOneNode, initializeGate))

    testEnv.start()
    return Env(testEnv, testEnv.workflowClient, fakeNodeActivity)
  }

  private fun startWorkflow(
    env: Env,
    model: ContinuumWorkflowModel,
    workflowId: String = "test-${UUID.randomUUID()}"
  ): Pair<IContinuumWorkflow, CompletableFuture<Map<String, Map<String, PortData>>>> {
    val stub = env.client.newWorkflowStub(
      IContinuumWorkflow::class.java,
      WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE)
        .build()
    )
    val future = WorkflowClient.execute(stub::start, model)
    return stub to future
  }

  /** One published status event, reduced to what the tests assert on. */
  private data class StatusEvent(
    val nodeStatuses: Map<String, ContinuumWorkflowModel.NodeStatus?>,
    val edgeStatuses: Map<String, ContinuumWorkflowModel.EdgeStatus?>,
    val progressPercentage: Int,
    val workflowStatus: String
  )

  /** Routes published status events into a list, one entry per event. */
  private fun captureStatusEvents(): MutableList<StatusEvent> {
    val events = CopyOnWriteArrayList<StatusEvent>()
    val bridge = mock<StreamBridge>()
    whenever(bridge.send(any<String>(), any<Any>())).thenAnswer { invocation ->
      val message = invocation.getArgument<Message<*>>(1)
      val update = (message.payload as WorkflowUpdateEvent).data
      events.add(
        StatusEvent(
          nodeStatuses = update.workflow.nodes.associate { it.id to it.data.status },
          edgeStatuses = update.workflow.edges.associate { it.id to it.data?.status },
          progressPercentage = update.progressPercentage,
          workflowStatus = update.status
        )
      )
      true
    }
    StatusHelper(bridge).postConstruct()
    return events
  }

  private fun samplePortData(value: String = "sample"): PortData =
    PortData(status = PortDataStatus.SUCCESS, contentType = "text/plain", tableSpec = emptyList(), data = value)

  private fun awaitInvoked(fake: FakeContinuumNodeActivity, nodeId: String, timeoutMs: Long = 5000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!fake.invokedNodeIds.contains(nodeId)) {
      if (System.currentTimeMillis() > deadline) error("Timed out waiting for node $nodeId to be invoked")
      Thread.sleep(10)
    }
  }

  @Test
  fun `happy path linear workflow completes and returns all node outputs`() {
    newEnv().use { env ->
      val events = captureStatusEvents()
      env.fakeNodeActivity.successOutputs["2"] =
        mapOf("output-1" to samplePortData("time"))
      env.fakeNodeActivity.successOutputs["3"] =
        mapOf("output-1" to samplePortData("part1"), "output-2" to samplePortData("part2"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      val result = future.get(10, TimeUnit.SECONDS)

      assertTrue(result.containsKey("2"))
      assertTrue(result.containsKey("3"))
      assertEquals(
        mapOf("reactflow__edge-2output-1-3input-1" to ContinuumWorkflowModel.EdgeStatus.DELIVERED),
        events.last().edgeStatuses
      )
    }
  }

  @Test
  fun `diamond DAG executes fan-out nodes and only runs fan-in node after both complete`() {
    newEnv().use { env ->
      listOf("A", "B", "C", "D").forEach { id ->
        env.fakeNodeActivity.successOutputs[id] = mapOf("out-1" to samplePortData(id))
      }

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/diamond-4node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      val result = future.get(10, TimeUnit.SECONDS)

      assertEquals(setOf("A", "B", "C", "D"), result.keys)

      val invoked = env.fakeNodeActivity.invokedNodeIds
      val dIndex = invoked.indexOf("D")
      assertTrue(dIndex > invoked.indexOf("B"))
      assertTrue(dIndex > invoked.indexOf("C"))
    }
  }

  private val variableKey = IContinuumNodeActivity.NodeOutputSystemPort.VARIABLE.key

  /** The flow variables [nodeId] received in `inputs["$variable"]`, or null when the key was absent. */
  private fun receivedFlowVariables(fake: FakeContinuumNodeActivity, nodeId: String): Map<String, Any>? {
    val inputs = fake.receivedInputs.getValue(nodeId)
    if (!inputs.containsKey(variableKey)) return null
    return FlowVariables.extract(inputs)
  }

  private fun runLinearThreeNode(env: Env): Map<String, Map<String, PortData>> {
    listOf("A", "B", "C").forEach { id -> env.fakeNodeActivity.successOutputs[id] = mapOf("out-1" to samplePortData(id)) }
    val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-3node.cwf.json")
    val (_, future) = startWorkflow(env, model)
    return future.get(10, TimeUnit.SECONDS)
  }

  @Test
  fun `flow variables accumulate down a linear chain while each node returns only its own`() {
    newEnv().use { env ->
      env.fakeNodeActivity.flowVariablesToSet["A"] = mapOf("x" to 1)
      env.fakeNodeActivity.flowVariablesToSet["B"] = mapOf("y" to 2)

      val result = runLinearThreeNode(env)

      assertEquals(null, receivedFlowVariables(env.fakeNodeActivity, "A"))
      assertEquals(mapOf("x" to 1), receivedFlowVariables(env.fakeNodeActivity, "B"))
      assertEquals(mapOf("x" to 1, "y" to 2), receivedFlowVariables(env.fakeNodeActivity, "C"))
      assertEquals(mapOf("y" to 2), FlowVariables.extract(result.getValue("B")), "B stores only what it set")
      assertFalse(result.getValue("C").containsKey(variableKey))
      // Data ports are forwarded exactly as before
      assertEquals(setOf("in-1", variableKey), env.fakeNodeActivity.receivedInputs.getValue("C").keys)
    }
  }

  @Test
  fun `a grandparent's flow variable passes through a node that sets none`() {
    newEnv().use { env ->
      env.fakeNodeActivity.flowVariablesToSet["A"] = mapOf("x" to 1)

      val result = runLinearThreeNode(env)

      assertEquals(mapOf("x" to 1), receivedFlowVariables(env.fakeNodeActivity, "C"))
      assertFalse(result.getValue("B").containsKey(variableKey))
    }
  }

  @Test
  fun `a flow variable overridden down the chain reaches later nodes with the new value`() {
    newEnv().use { env ->
      env.fakeNodeActivity.flowVariablesToSet["A"] = mapOf("x" to 1)
      env.fakeNodeActivity.flowVariablesToSet["B"] = mapOf("x" to 10)

      runLinearThreeNode(env)

      assertEquals(mapOf("x" to 1), receivedFlowVariables(env.fakeNodeActivity, "B"))
      assertEquals(mapOf("x" to 10), receivedFlowVariables(env.fakeNodeActivity, "C"))
    }
  }

  @Test
  fun `fan-in keeps the flow variable from the lower input port and merges identical values`() {
    newEnv().use { env ->
      listOf("A", "B", "C", "D").forEach { id -> env.fakeNodeActivity.successOutputs[id] = mapOf("out-1" to samplePortData(id)) }
      env.fakeNodeActivity.flowVariablesToSet["A"] = mapOf("shared" to "root")
      env.fakeNodeActivity.flowVariablesToSet["B"] = mapOf("k" to "from-B", "same" to 5)
      env.fakeNodeActivity.flowVariablesToSet["C"] = mapOf("k" to "from-C", "same" to 5)

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/diamond-4node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      future.get(10, TimeUnit.SECONDS)

      // D's in-1 is fed by B, in-2 by C: B is the lower port
      assertEquals(
        mapOf("shared" to "root", "k" to "from-B", "same" to 5),
        receivedFlowVariables(env.fakeNodeActivity, "D")
      )
    }
  }

  @Test
  fun `a workflow without flow variables sends and stores exactly the data ports`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["2"] = mapOf("output-1" to samplePortData("time"))
      env.fakeNodeActivity.successOutputs["3"] =
        mapOf("output-1" to samplePortData("part1"), "output-2" to samplePortData("part2"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      val result = future.get(10, TimeUnit.SECONDS)

      assertEquals(emptySet(), env.fakeNodeActivity.receivedInputs.getValue("2").keys)
      assertEquals(setOf("input-1"), env.fakeNodeActivity.receivedInputs.getValue("3").keys)
      assertEquals(setOf("output-1"), result.getValue("2").keys)
      assertEquals(setOf("output-1", "output-2"), result.getValue("3").keys)
    }
  }

  @Test
  fun `node returning dollar-error output fails workflow with ApplicationFailure and preserves prior successes`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["2"] =
        mapOf("output-1" to samplePortData("time"))
      env.fakeNodeActivity.errorOutputs["3"] =
        mapOf(IContinuumNodeActivity.NodeOutputSystemPort.ERROR.key to samplePortData("boom"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> { future.get(10, TimeUnit.SECONDS) }
      val workflowFailed = executionException.cause as WorkflowFailedException
      val appFailure = workflowFailed.cause as ApplicationFailure
      assertEquals("WorkflowExecutionFailed", appFailure.type)

      @Suppress("UNCHECKED_CAST")
      val details = appFailure.details.get(0, Map::class.java) as Map<String, Map<*, *>>
      assertTrue(details.getValue("nodeErrors").containsKey("3"))
      assertTrue(details.getValue("nodeToOutputsMap").containsKey("2"))
    }
  }

  @Test
  fun `partial task queue resolution fails workflow before any node activity executes`() {
    newEnv(dropOneNode = true).use { env ->
      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> { future.get(10, TimeUnit.SECONDS) }
      val appFailure = (executionException.cause as WorkflowFailedException).cause as ApplicationFailure
      assertEquals("InitializationFailed", appFailure.type)
      assertTrue(env.fakeNodeActivity.invokedNodeIds.isEmpty())
    }
  }

  @Test
  fun `getWorkflowSnapshot query returns current outputs mid-execution and after completion`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["2"] =
        mapOf("output-1" to samplePortData("time"))
      env.fakeNodeActivity.successOutputs["3"] =
        mapOf("output-1" to samplePortData("part1"))
      val latch = CountDownLatch(1)
      env.fakeNodeActivity.blockUntil["3"] = latch

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (stub, future) = startWorkflow(env, model)

      awaitInvoked(env.fakeNodeActivity, "3")
      val midSnapshot = stub.getWorkflowSnapshot()
      assertTrue(midSnapshot.nodeToOutputsMap.containsKey("2"))
      assertFalse(midSnapshot.nodeToOutputsMap.containsKey("3"))

      latch.countDown()
      future.get(10, TimeUnit.SECONDS)

      val finalSnapshot = stub.getWorkflowSnapshot()
      assertTrue(finalSnapshot.nodeToOutputsMap.containsKey("2"))
      assertTrue(finalSnapshot.nodeToOutputsMap.containsKey("3"))
    }
  }

  @Test
  fun `updateNodeProgressSignal updates progress without disrupting execution`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["2"] =
        mapOf("output-1" to samplePortData("time"))
      env.fakeNodeActivity.successOutputs["3"] =
        mapOf("output-1" to samplePortData("part1"))
      val latch = CountDownLatch(1)
      env.fakeNodeActivity.blockUntil["3"] = latch

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (stub, future) = startWorkflow(env, model)

      awaitInvoked(env.fakeNodeActivity, "3")
      stub.updateNodeProgressSignal(
        ContinuumNodeActivitySignal(
          nodeId = "3",
          nodeProgress = NodeProgress(progressPercentage = 42, message = "halfway")
        )
      )

      latch.countDown()
      val result = future.get(10, TimeUnit.SECONDS)
      assertTrue(result.containsKey("3"))
    }
  }

  @Test
  fun `updateNodeProgressSignal with unknown node id fails the workflow task, not the workflow execution`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["2"] =
        mapOf("output-1" to samplePortData("time"))
      val latch = CountDownLatch(1)
      env.fakeNodeActivity.blockUntil["3"] = latch

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val workflowId = "test-unknown-node-signal-${UUID.randomUUID()}"
      val (stub, _) = startWorkflow(env, model, workflowId)

      awaitInvoked(env.fakeNodeActivity, "3")
      stub.updateNodeProgressSignal(
        ContinuumNodeActivitySignal(
          nodeId = "does-not-exist",
          nodeProgress = NodeProgress(progressPercentage = 0)
        )
      )

      // The internal NPE on an unknown node id fails the *workflow task*, not the workflow
      // execution — it never surfaces via the result future, only in execution history.
      val deadline = System.currentTimeMillis() + 5000
      var hasTaskFailure = false
      while (System.currentTimeMillis() < deadline && !hasTaskFailure) {
        val history = env.client.fetchHistory(workflowId)
        hasTaskFailure = history.events.any { it.hasWorkflowTaskFailedEventAttributes() }
        if (!hasTaskFailure) Thread.sleep(50)
      }
      assertTrue(hasTaskFailure, "Expected a WorkflowTaskFailed event from the unguarded NPE")

      latch.countDown()
      WorkflowStub.fromTyped(stub).terminate("test cleanup")
    }
  }

  @Test
  fun `node with unconnected declared input port is skipped while independent siblings still complete`() {
    newEnv().use { env ->
      val events = captureStatusEvents()
      env.fakeNodeActivity.successOutputs["root"] = mapOf("out-1" to samplePortData("root"))
      env.fakeNodeActivity.successOutputs["independent"] = mapOf("out-1" to samplePortData("independent"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/skipped-input-3node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      val result = future.get(10, TimeUnit.SECONDS)

      assertEquals(setOf("root", "independent"), result.keys)
      assertFalse(env.fakeNodeActivity.invokedNodeIds.contains("skip-me"))
      val finalEvent = events.last()
      assertEquals(ContinuumWorkflowModel.NodeStatus.SKIPPED, finalEvent.nodeStatuses["skip-me"])
      assertEquals("FINISHED", finalEvent.workflowStatus)
      // Skipped nodes count as done
      assertEquals(100, finalEvent.progressPercentage)
    }
  }

  @Test
  fun `a node is published as SCHEDULED when dispatched and BUSY once its activity starts`() {
    newEnv().use { env ->
      val events = captureStatusEvents()
      env.fakeNodeActivity.successOutputs["2"] = mapOf("output-1" to samplePortData("time"))
      env.fakeNodeActivity.successOutputs["3"] = mapOf("output-1" to samplePortData("part1"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (_, future) = startWorkflow(env = env, model = model)
      future.get(10, TimeUnit.SECONDS)

      val distinctStatusesOfNode2 = events.map { it.nodeStatuses["2"] }
        .fold(mutableListOf<ContinuumWorkflowModel.NodeStatus?>()) { statuses, status ->
          if (statuses.lastOrNull() != status) statuses.add(status)
          statuses
        }
      val expected = listOf(
        ContinuumWorkflowModel.NodeStatus.SCHEDULED,
        ContinuumWorkflowModel.NodeStatus.BUSY,
        ContinuumWorkflowModel.NodeStatus.SUCCESS
      )
      assertEquals(expected, distinctStatusesOfNode2.filterNotNull())
    }
  }

  @Test
  fun `cancellation while a node is busy marks the workflow cancelled and rethrows to the client`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["2"] =
        mapOf("output-1" to samplePortData("time"))
      env.fakeNodeActivity.blockUntil["3"] = CountDownLatch(1) // never released

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (stub, future) = startWorkflow(env, model)

      awaitInvoked(env.fakeNodeActivity, "3")
      WorkflowStub.fromTyped(stub).cancel()

      val executionException = assertFailsWith<ExecutionException> { future.get(10, TimeUnit.SECONDS) }
      val workflowFailed = executionException.cause as WorkflowFailedException
      assertTrue(workflowFailed.cause is CanceledFailure)
    }
  }

  @Test
  fun `cancelling signal sent before the workflow model is stored does not wedge the workflow`() {
    // The API server sends notifyCancelling right after the start. If that lands while start() is still resolving
    // task queues, currentRunningWorkflow is still null. The signal handler used to NPE (publishUpdate used `!!`),
    // which fails the workflow TASK, not the workflow: Temporal retries it forever and the run stays RUNNING.
    val gate = CountDownLatch(1)
    newEnv(initializeGate = gate).use { env ->
      env.fakeNodeActivity.successOutputs["1"] = mapOf("output-1" to samplePortData("ok"))
      val model = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val (stub, future) = startWorkflow(env, model)

      // Give the workflow time to reach the held initialize activity, then signal while it is parked there.
      Thread.sleep(500)
      stub.notifyCancelling("cancelled right after start")
      gate.countDown()

      // The cancel signal only pins the final status; it does not stop the workflow. The workflow must still
      // run to completion instead of failing its workflow task on every retry.
      val result = future.get(10, TimeUnit.SECONDS)
      assertTrue(result.containsKey("1"))
    }
  }

  @Test
  fun `terminating signal sent before the workflow model is stored does not wedge the workflow`() {
    val gate = CountDownLatch(1)
    newEnv(initializeGate = gate).use { env ->
      env.fakeNodeActivity.successOutputs["1"] = mapOf("output-1" to samplePortData("ok"))
      val model = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val (stub, future) = startWorkflow(env, model)

      Thread.sleep(500)
      stub.notifyTerminating("terminated right after start")
      gate.countDown()

      val result = future.get(10, TimeUnit.SECONDS)
      assertTrue(result.containsKey("1"))
    }
  }

  @Test
  fun `node throwing retryable exception retries and eventually succeeds`() {
    newEnv().use { env ->
      // Configure fake to throw exception on first 2 attempts, succeed on 3rd
      val attemptCount = AtomicInteger(0)
      env.fakeNodeActivity.throwOnAttempt["1"] = {
        if (attemptCount.incrementAndGet() <= 2) {
          throw ApplicationFailure.newFailure(
            "Transient network error",
            "NetworkError"
          ) // retryable by default
        }
      }
      env.fakeNodeActivity.successOutputs["1"] = mapOf("output-1" to samplePortData("success"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      val result = future.get(30, TimeUnit.SECONDS)

      assertTrue(result.containsKey("1"))
      assertTrue(attemptCount.get() >= 3, "Expected at least 3 attempts but got ${attemptCount.get()}")
    }
  }

  @Test
  fun `node throwing exception test with linear workflow`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["2"] = mapOf("output-1" to samplePortData("first-success"))
      env.fakeNodeActivity.alwaysThrow["3"] = RuntimeException("Test exception")

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> {
        future.get(30, TimeUnit.SECONDS)
      }
      println("Exception caught: ${executionException.cause}")
      println("Invoked nodes: ${env.fakeNodeActivity.invokedNodeIds}")
      println("Attempt count for node 3: ${env.fakeNodeActivity.attemptCount["3"]?.get()}")
      
      val workflowFailed = executionException.cause as WorkflowFailedException
      assertTrue(workflowFailed.cause is ApplicationFailure)
      assertTrue(env.fakeNodeActivity.attemptCount["3"]!!.get() > 1, "Should have retried, got ${env.fakeNodeActivity.attemptCount["3"]!!.get()}")
    }
  }

  @Test
  fun `single node workflow executes successfully`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["1"] = mapOf("output-1" to samplePortData("single-success"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      val result = future.get(10, TimeUnit.SECONDS)

      println("Invoked nodes: ${env.fakeNodeActivity.invokedNodeIds}")
      println("Attempt count for node 1: ${env.fakeNodeActivity.attemptCount["1"]?.get()}")
      assertTrue(result.containsKey("1"))
      assertTrue(env.fakeNodeActivity.invokedNodeIds.contains("1"))
    }
  }

  @Test
  fun `node throwing retryable exception exhausts retries and fails workflow`() {
    newEnv().use { env ->
      // Configure to always throw retryable exception
      env.fakeNodeActivity.alwaysThrow["1"] = RuntimeException("Persistent error")
      // Don't set successOutputs - we want this to always fail

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val (_, future) = startWorkflow(env, model)

      // Note: With 500 max retry attempts, this would take too long.
      // In a real scenario, you'd configure shorter retry options for testing.
      // For now, we verify that it does retry multiple times within a reasonable timeout.
      val executionException = assertFailsWith<ExecutionException> {
        future.get(30, TimeUnit.SECONDS)
      }
      val workflowFailed = executionException.cause as WorkflowFailedException
      assertTrue(workflowFailed.cause is ApplicationFailure)
      assertTrue(env.fakeNodeActivity.attemptCount["1"]!!.get() > 1, "Should have retried multiple times, got ${env.fakeNodeActivity.attemptCount["1"]!!.get()}")
    }
  }

  @Test
  fun `node with maximumAttempts override fails faster than the workflow default`() {
    newEnv().use { env ->
      env.fakeNodeActivity.alwaysThrow["1"] = RuntimeException("Persistent error")

      val baseModel = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val overriddenNode = baseModel.nodes[0].copy(
        data = baseModel.nodes[0].data.copy(
          retryOptions = ContinuumWorkflowModel.RetryOptionsConfig(maximumAttempts = 2)
        )
      )
      val model = baseModel.copy(nodes = listOf(overriddenNode))

      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> {
        future.get(30, TimeUnit.SECONDS)
      }
      val workflowFailed = executionException.cause as WorkflowFailedException
      assertTrue(workflowFailed.cause is ApplicationFailure)
      assertEquals(
        2, env.fakeNodeActivity.attemptCount["1"]!!.get(),
        "Override should cap attempts at 2, got ${env.fakeNodeActivity.attemptCount["1"]!!.get()}"
      )
    }
  }

  @Test
  fun `nodes of the same type in one workflow retry independently of each other`() {
    newEnv().use { env ->
      env.fakeNodeActivity.alwaysThrow["1"] = RuntimeException("fail-1")
      env.fakeNodeActivity.alwaysThrow["2"] = RuntimeException("fail-2")

      val baseModel = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val baseNode = baseModel.nodes[0]
      val nodeOne = baseNode.copy(
        id = "1",
        data = baseNode.data.copy(
          retryOptions = ContinuumWorkflowModel.RetryOptionsConfig(maximumAttempts = 2)
        )
      )
      val nodeTwo = baseNode.copy(
        id = "2",
        data = baseNode.data.copy(
          retryOptions = ContinuumWorkflowModel.RetryOptionsConfig(maximumAttempts = 3)
        )
      )
      val model = baseModel.copy(nodes = listOf(nodeOne, nodeTwo), edges = emptyList())

      val (_, future) = startWorkflow(env, model)

      assertFailsWith<ExecutionException> {
        future.get(30, TimeUnit.SECONDS)
      }
      assertEquals(
        2, env.fakeNodeActivity.attemptCount["1"]!!.get(),
        "Node 1's override should cap attempts at 2, got ${env.fakeNodeActivity.attemptCount["1"]!!.get()}"
      )
      assertEquals(
        3, env.fakeNodeActivity.attemptCount["2"]!!.get(),
        "Node 2's override should cap attempts at 3, got ${env.fakeNodeActivity.attemptCount["2"]!!.get()}"
      )
    }
  }

  @Test
  fun `node without a retryOptions override falls back to the workflow default even when a sibling node has a low override`() {
    newEnv().use { env ->
      env.fakeNodeActivity.alwaysThrow["1"] = RuntimeException("fail-1")
      val attemptCount = AtomicInteger(0)
      env.fakeNodeActivity.throwOnAttempt["2"] = {
        if (attemptCount.incrementAndGet() < 4) {
          throw ApplicationFailure.newFailure("Transient error", "NetworkError")
        }
      }
      env.fakeNodeActivity.successOutputs["2"] = mapOf("output-1" to samplePortData("success"))

      val baseModel = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val baseNode = baseModel.nodes[0]
      val overriddenNode = baseNode.copy(
        id = "1",
        data = baseNode.data.copy(
          retryOptions = ContinuumWorkflowModel.RetryOptionsConfig(maximumAttempts = 2)
        )
      )
      val defaultNode = baseNode.copy(id = "2")
      val model = baseModel.copy(nodes = listOf(overriddenNode, defaultNode), edges = emptyList())

      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> {
        future.get(30, TimeUnit.SECONDS)
      }
      val workflowFailed = executionException.cause as WorkflowFailedException
      val appFailure = workflowFailed.cause as ApplicationFailure

      @Suppress("UNCHECKED_CAST")
      val details = appFailure.details.get(0, Map::class.java) as Map<String, Map<*, *>>
      assertTrue(details.getValue("nodeErrors").containsKey("1"))
      assertTrue(details.getValue("nodeToOutputsMap").containsKey("2"))

      assertEquals(
        2, env.fakeNodeActivity.attemptCount["1"]!!.get(),
        "Overridden node should stop retrying at 2 attempts"
      )
      assertTrue(
        env.fakeNodeActivity.attemptCount["2"]!!.get() >= 4,
        "Default node should retry past the sibling's low cap, got ${env.fakeNodeActivity.attemptCount["2"]!!.get()}"
      )
    }
  }

  @Test
  fun `doNotRetry override fails immediately for a normally-retryable error type`() {
    newEnv().use { env ->
      env.fakeNodeActivity.alwaysThrow["1"] =
        ApplicationFailure.newFailure("Fatal but usually retryable", "SpecialErrorType")

      val baseModel = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val overriddenNode = baseModel.nodes[0].copy(
        data = baseModel.nodes[0].data.copy(
          retryOptions = ContinuumWorkflowModel.RetryOptionsConfig(doNotRetry = listOf("SpecialErrorType"))
        )
      )
      val model = baseModel.copy(nodes = listOf(overriddenNode))

      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> {
        future.get(10, TimeUnit.SECONDS)
      }
      val workflowFailed = executionException.cause as WorkflowFailedException
      assertTrue(workflowFailed.cause is ApplicationFailure)
      assertEquals(
        1, env.fakeNodeActivity.attemptCount["1"]!!.get(),
        "doNotRetry override should stop retries after the first attempt, got ${env.fakeNodeActivity.attemptCount["1"]!!.get()}"
      )
    }
  }

  @Test
  fun `initialIntervalSeconds, backoffCoefficient, and maximumIntervalSeconds overrides shape the simulated backoff delay between attempts`() {
    newEnv().use { env ->
      val attemptCount = AtomicInteger(0)
      env.fakeNodeActivity.throwOnAttempt["1"] = {
        if (attemptCount.incrementAndGet() < 3) {
          throw ApplicationFailure.newFailure("Transient error", "NetworkError")
        }
      }
      env.fakeNodeActivity.successOutputs["1"] = mapOf("output-1" to samplePortData("success"))

      val baseModel = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val overriddenNode = baseModel.nodes[0].copy(
        data = baseModel.nodes[0].data.copy(
          retryOptions = ContinuumWorkflowModel.RetryOptionsConfig(
            initialIntervalSeconds = 10,
            backoffCoefficient = 2.0,
            maximumIntervalSeconds = 100,
            maximumAttempts = 5
          )
        )
      )
      val model = baseModel.copy(nodes = listOf(overriddenNode))

      val startTimeMillis = env.testEnv.currentTimeMillis()
      val (_, future) = startWorkflow(env, model)
      val result = future.get(30, TimeUnit.SECONDS)
      val elapsedSimulatedMillis = env.testEnv.currentTimeMillis() - startTimeMillis

      assertTrue(result.containsKey("1"))
      assertEquals(3, attemptCount.get())
      // Expected backoff before the 3rd (successful) attempt: 10s (attempt 1->2) + 20s (attempt 2->3) = 30s.
      // If the override weren't applied, the workflow default (initialInterval 1s, coefficient 2.0) would
      // only need ~3s, so this lower bound also proves the override actually took effect. We only assert
      // a lower bound (not an upper bound) since TestWorkflowEnvironment's auto-time-skipping clock can
      // jump forward by unrelated amounts once the workflow completes (e.g. past other ActivityOptions'
      // StartToCloseTimeout), which would make an upper-bound assertion flaky.
      assertTrue(
        elapsedSimulatedMillis >= 30_000,
        "Expected at least 30s of simulated backoff delay from the override, got ${elapsedSimulatedMillis}ms"
      )
    }
  }

  @Test
  fun `node throwing non-retryable exception immediately fails without retry`() {
    newEnv().use { env ->
      env.fakeNodeActivity.throwOnInvoke["1"] = {
        throw ApplicationFailure.newNonRetryableFailure(
          "Invalid configuration",
          "ConfigError"
        )
      }
      // Don't set successOutputs - we want this to throw immediately

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/single-node.cwf.json")
      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> {
        future.get(10, TimeUnit.SECONDS)
      }
      val workflowFailed = executionException.cause as WorkflowFailedException
      assertTrue(workflowFailed.cause is ApplicationFailure)
      assertEquals(1, env.fakeNodeActivity.attemptCount["1"]!!.get(), "Should only attempt once for non-retryable, got ${env.fakeNodeActivity.attemptCount["1"]!!.get()}")
    }
  }

  @Test
  fun `multiple nodes failing with different error types tracks all errors`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["A"] = mapOf("out-1" to samplePortData("A"))
      env.fakeNodeActivity.errorOutputs["B"] = mapOf("\$error" to samplePortData("B failed"))
      env.fakeNodeActivity.errorOutputs["C"] = mapOf("\$error" to samplePortData("C failed"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/diamond-4node.cwf.json")
      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> { future.get(10, TimeUnit.SECONDS) }
      val workflowFailed = executionException.cause as WorkflowFailedException
      val appFailure = workflowFailed.cause as ApplicationFailure

      @Suppress("UNCHECKED_CAST")
      val details = appFailure.details.get(0, Map::class.java) as Map<String, Map<*, *>>
      val nodeErrors = details.getValue("nodeErrors")

      assertEquals(2, nodeErrors.size)
      assertTrue(nodeErrors.containsKey("B"))
      assertTrue(nodeErrors.containsKey("C"))
      // D should not execute since B and C failed
      assertFalse(env.fakeNodeActivity.invokedNodeIds.contains("D"))
    }
  }

  @Test
  fun `failure at workflow start prevents downstream nodes from executing`() {
    newEnv().use { env ->
      val events = captureStatusEvents()
      env.fakeNodeActivity.errorOutputs["A"] = mapOf("\$error" to samplePortData("root failed"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/diamond-4node.cwf.json")
      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> { future.get(10, TimeUnit.SECONDS) }

      // Only root node should have been invoked
      assertEquals(listOf("A"), env.fakeNodeActivity.invokedNodeIds)
      val finalEvent = events.last()
      assertEquals(
        mapOf(
          "A" to ContinuumWorkflowModel.NodeStatus.FAILED,
          "B" to ContinuumWorkflowModel.NodeStatus.SKIPPED,
          "C" to ContinuumWorkflowModel.NodeStatus.SKIPPED,
          "D" to ContinuumWorkflowModel.NodeStatus.SKIPPED
        ),
        finalEvent.nodeStatuses
      )
      // Every edge leads into a skipped node, so every wire is broken
      val broken = ContinuumWorkflowModel.EdgeStatus.BROKEN
      assertEquals(
        mapOf("edge-A-B" to broken, "edge-A-C" to broken, "edge-B-D" to broken, "edge-C-D" to broken),
        finalEvent.edgeStatuses
      )
      assertEquals(100, finalEvent.progressPercentage)
      // Skipped nodes are not errors
      @Suppress("UNCHECKED_CAST")
      val details = ((executionException.cause as WorkflowFailedException).cause as ApplicationFailure)
        .details.get(0, Map::class.java) as Map<String, Map<*, *>>
      assertEquals(setOf("A"), details.getValue("nodeErrors").keys)
    }
  }

  @Test
  fun `failure in middle of DAG allows independent branches to complete`() {
    newEnv().use { env ->
      val events = captureStatusEvents()
      env.fakeNodeActivity.successOutputs["A"] = mapOf("out-1" to samplePortData("A"))
      env.fakeNodeActivity.successOutputs["B"] = mapOf("out-1" to samplePortData("B"))
      env.fakeNodeActivity.errorOutputs["C"] = mapOf("\$error" to samplePortData("C failed"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/diamond-4node.cwf.json")
      val (_, future) = startWorkflow(env, model)

      val executionException = assertFailsWith<ExecutionException> { future.get(10, TimeUnit.SECONDS) }
      val workflowFailed = executionException.cause as WorkflowFailedException
      val appFailure = workflowFailed.cause as ApplicationFailure

      @Suppress("UNCHECKED_CAST")
      val details = appFailure.details.get(0, Map::class.java) as Map<String, Map<*, *>>

      // B succeeded even though C failed (parallel branches)
      assertTrue(details.getValue("nodeToOutputsMap").containsKey("B"))
      assertTrue(details.getValue("nodeErrors").containsKey("C"))
      // The join is SKIPPED (not null) and is not an error
      val finalEvent = events.last()
      assertEquals(ContinuumWorkflowModel.NodeStatus.SKIPPED, finalEvent.nodeStatuses["D"])
      assertEquals(ContinuumWorkflowModel.NodeStatus.SUCCESS, finalEvent.nodeStatuses["B"])
      assertFalse(details.getValue("nodeErrors").containsKey("D"))
      assertFalse(env.fakeNodeActivity.invokedNodeIds.contains("D"))
      // Only the wire out of the failed node is broken; the others delivered their data
      val delivered = ContinuumWorkflowModel.EdgeStatus.DELIVERED
      assertEquals(
        mapOf(
          "edge-A-B" to delivered,
          "edge-A-C" to delivered,
          "edge-B-D" to delivered,
          "edge-C-D" to ContinuumWorkflowModel.EdgeStatus.BROKEN
        ),
        finalEvent.edgeStatuses
      )
    }
  }

  @Test
  fun `join is skipped and its wire broken when a parent succeeds without producing the connected port`() {
    newEnv().use { env ->
      val events = captureStatusEvents()
      env.fakeNodeActivity.successOutputs["A"] = mapOf("out-1" to samplePortData("A"))
      // B declares out-2 (wired to D) but only produces out-1
      env.fakeNodeActivity.successOutputs["B"] = mapOf("out-1" to samplePortData("B"))
      env.fakeNodeActivity.successOutputs["C"] = mapOf("out-1" to samplePortData("C"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/diamond-join-4node.cwf.json")
      val (_, future) = startWorkflow(env = env, model = model)
      val result = future.get(10, TimeUnit.SECONDS)

      // A skipped node does not fail the workflow
      assertEquals(setOf("A", "B", "C"), result.keys)
      assertFalse(env.fakeNodeActivity.invokedNodeIds.contains("D"))
      val finalEvent = events.last()
      assertEquals("FINISHED", finalEvent.workflowStatus)
      assertEquals(ContinuumWorkflowModel.NodeStatus.SKIPPED, finalEvent.nodeStatuses["D"])
      assertEquals(ContinuumWorkflowModel.EdgeStatus.BROKEN, finalEvent.edgeStatuses["edge-B-D"])
      assertEquals(ContinuumWorkflowModel.EdgeStatus.DELIVERED, finalEvent.edgeStatuses["edge-C-D"])
      assertEquals(100, finalEvent.progressPercentage)
    }
  }

  @Test
  fun `node fails on first attempt then succeeds on retry preserving previous successful nodes`() {
    newEnv().use { env ->
      env.fakeNodeActivity.successOutputs["2"] = mapOf("output-1" to samplePortData("first-success"))

      val attemptCount = AtomicInteger(0)
      env.fakeNodeActivity.throwOnAttempt["3"] = {
        if (attemptCount.incrementAndGet() == 1) {
          throw ApplicationFailure.newFailure("Temporary error", "TempError")
        }
      }
      env.fakeNodeActivity.successOutputs["3"] = mapOf("output-1" to samplePortData("retry-success"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      val result = future.get(30, TimeUnit.SECONDS)

      assertTrue(result.containsKey("2"))
      assertTrue(result.containsKey("3"))
      assertTrue(attemptCount.get() >= 2, "Expected at least 2 attempts for node 3")
    }
  }
}
