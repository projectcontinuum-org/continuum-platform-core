package org.projectcontinuum.core.orchestration.workflow

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.api.enums.v1.IndexedValueType
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowFailedException
import io.temporal.client.WorkflowOptions
import io.temporal.client.WorkflowStub
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.failure.ActivityFailure
import io.temporal.failure.ApplicationFailure
import io.temporal.failure.CanceledFailure
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.protocol.progress.ContinuumNodeActivitySignal
import org.projectcontinuum.core.commons.protocol.progress.NodeProgress
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import org.projectcontinuum.core.orchestration.workflow.fixtures.FakeContinuumNodeActivity
import org.projectcontinuum.core.orchestration.workflow.fixtures.FakeInitializeActivity
import org.projectcontinuum.core.orchestration.workflow.fixtures.WorkflowFixtures
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
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

  private class Env(
    val testEnv: TestWorkflowEnvironment,
    val client: WorkflowClient,
    val fakeNodeActivity: FakeContinuumNodeActivity
  ) : Closeable {
    override fun close() = testEnv.close()
  }

  private fun newEnv(dropOneNode: Boolean = false): Env {
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
      .registerActivitiesImplementations(FakeInitializeActivity(activityTaskQueue, dropOneNode))

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
      env.fakeNodeActivity.successOutputs["2"] =
        mapOf("output-1" to samplePortData("time"))
      env.fakeNodeActivity.successOutputs["3"] =
        mapOf("output-1" to samplePortData("part1"), "output-2" to samplePortData("part2"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      val result = future.get(10, TimeUnit.SECONDS)

      assertTrue(result.containsKey("2"))
      assertTrue(result.containsKey("3"))
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
      env.fakeNodeActivity.successOutputs["root"] = mapOf("out-1" to samplePortData("root"))
      env.fakeNodeActivity.successOutputs["independent"] = mapOf("out-1" to samplePortData("independent"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/skipped-input-3node.cwf.json")
      val (_, future) = startWorkflow(env, model)
      val result = future.get(10, TimeUnit.SECONDS)

      assertEquals(setOf("root", "independent"), result.keys)
      assertFalse(env.fakeNodeActivity.invokedNodeIds.contains("skip-me"))
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
      env.fakeNodeActivity.errorOutputs["A"] = mapOf("\$error" to samplePortData("root failed"))

      val model = WorkflowFixtures.loadWorkflow("/test-workflows/diamond-4node.cwf.json")
      val (_, future) = startWorkflow(env, model)

      assertFailsWith<ExecutionException> { future.get(10, TimeUnit.SECONDS) }

      // Only root node should have been invoked
      assertEquals(listOf("A"), env.fakeNodeActivity.invokedNodeIds)
    }
  }

  @Test
  fun `failure in middle of DAG allows independent branches to complete`() {
    newEnv().use { env ->
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
