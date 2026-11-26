package org.projectcontinuum.core.orchestration.workflow

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.api.enums.v1.IndexedValueType
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowOptions
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.model.WorkflowUpdateEvent
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.utils.ValidationHelper
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import org.projectcontinuum.core.orchestration.utils.StatusHelper
import org.projectcontinuum.core.orchestration.workflow.fixtures.FakeContinuumNodeActivity
import org.projectcontinuum.core.orchestration.workflow.fixtures.FakeInitializeActivity
import org.projectcontinuum.core.orchestration.workflow.fixtures.ScriptedLoopConditionEvaluator
import org.projectcontinuum.core.orchestration.workflow.fixtures.WorkflowFixtures
import org.projectcontinuum.core.orchestration.workflow.loop.LoopConditionEvaluators
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.messaging.Message
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A workflow with a loop runs through the orchestrator's plumbing. The loop
 * gets no task queue and no activity, workers receive their node without the loop fields, and the progress ignores the
 * loop node. The loop's condition is scripted to stop after the first iteration, so every node runs once.
 */
class ContinuumWorkflowLoopPlumbingTest {

  private val activityTaskQueue = "TEST_LOOP_PLUMBING_NODE_ACTIVITY_TASK_QUEUE"
  private val fixture = "/test-workflows/loop-linear-3member.cwf.json"
  private val memberIds = listOf("fetch", "parse", "append")

  private class Env(
    val testEnv: TestWorkflowEnvironment,
    val client: WorkflowClient,
    val fakeNodeActivity: FakeContinuumNodeActivity,
    val fakeInitializeActivity: FakeInitializeActivity
  ) : Closeable {
    override fun close() = testEnv.close()
  }

  private data class PublishedState(
    val loopStatus: ContinuumWorkflowModel.NodeStatus?,
    val progressPercentage: Int,
    val workflowStatus: String
  )

  @BeforeEach
  fun installEvaluator() {
    LoopConditionEvaluators.install(evaluator = ScriptedLoopConditionEvaluator())
  }

  @AfterEach
  fun resetStatusPublisher() {
    StatusHelper.reset()
    LoopConditionEvaluators.reset()
  }

  private fun newEnv(): Env {
    val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper().apply { registerModule(KotlinModule.Builder().build()) }
    val dataConverter = DefaultDataConverter.newDefaultInstance().withPayloadConverterOverrides(JacksonJsonPayloadConverter(mapper))
    val testEnv = TestWorkflowEnvironment.newInstance(
      TestEnvironmentOptions.newBuilder()
        .setWorkflowClientOptions(WorkflowClientOptions.newBuilder().setDataConverter(dataConverter).build())
        .registerSearchAttribute("Continuum:ExecutionStatus", IndexedValueType.INDEXED_VALUE_TYPE_INT)
        .registerSearchAttribute("Continuum:WorkflowFileName", IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD)
        .build()
    )
    testEnv.newWorker(TaskQueues.WORKFLOW_TASK_QUEUE).registerWorkflowImplementationTypes(ContinuumWorkflow::class.java)
    val fakeNodeActivity = FakeContinuumNodeActivity()
    testEnv.newWorker(activityTaskQueue).registerActivitiesImplementations(fakeNodeActivity)
    val fakeInitializeActivity = FakeInitializeActivity(taskQueueForAllNodes = activityTaskQueue)
    testEnv.newWorker(TaskQueues.ACTIVITY_TASK_QUEUE_INITIALIZE).registerActivitiesImplementations(fakeInitializeActivity)
    testEnv.start()
    return Env(
      testEnv = testEnv,
      client = testEnv.workflowClient,
      fakeNodeActivity = fakeNodeActivity,
      fakeInitializeActivity = fakeInitializeActivity
    )
  }

  private fun captureStatusEvents(): MutableList<PublishedState> {
    val events = CopyOnWriteArrayList<PublishedState>()
    val bridge = mock<StreamBridge>()
    whenever(bridge.send(any<String>(), any<Any>())).thenAnswer { invocation ->
      val update = (invocation.getArgument<Message<*>>(1).payload as WorkflowUpdateEvent).data
      events.add(
        PublishedState(
          loopStatus = update.workflow.nodes.first { node -> node.id == "L" }.data.status,
          progressPercentage = update.progressPercentage,
          workflowStatus = update.status
        )
      )
      true
    }
    StatusHelper(bridge).postConstruct()
    return events
  }

  private fun samplePortData(value: String) =
    PortData(status = PortDataStatus.SUCCESS, contentType = "text/plain", tableSpec = emptyList(), data = value)

  private fun runFixture(env: Env): Map<String, Map<String, PortData>> {
    listOf("seed", "fetch", "parse", "append", "write").forEach { nodeId ->
      env.fakeNodeActivity.successOutputs[nodeId] = mapOf("out" to samplePortData(value = nodeId))
    }
    val stub = env.client.newWorkflowStub(
      IContinuumWorkflow::class.java,
      WorkflowOptions.newBuilder()
        .setWorkflowId("loop-plumbing-${UUID.randomUUID()}")
        .setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE)
        .build()
    )
    val future = WorkflowClient.execute(stub::start, WorkflowFixtures.loadWorkflow(classpathResource = fixture))
    return future.get(10, TimeUnit.SECONDS)
  }

  @Test
  fun `the fixture's loop properties are valid against the Loop descriptor`() {
    val loop = WorkflowFixtures.loadWorkflow(classpathResource = fixture).nodes.single { node -> LoopNode.isLoop(node = node) }

    assertEquals(LoopNode.NODE_TYPE, loop.type)
    assertEquals(emptyList(), ValidationHelper.findSchemaViolations(loop.data.properties, LoopNodeDescriptor.propertiesSchema))
  }

  @Test
  fun `the loop gets no task queue and the run does not fail with InitializationFailed`() {
    newEnv().use { env ->
      val result = runFixture(env = env)

      val requested = env.fakeInitializeActivity.requestedNodeIds.flatten().toSet()
      assertTrue(requested.isNotEmpty())
      assertTrue(LoopNode.NODE_MODEL !in requested, "the loop must not be resolved to a task queue: $requested")
      assertEquals(
        setOf(
          "org.projectcontinuum.test.node.SetSeedNodeModel",
          "org.projectcontinuum.test.node.FetchPageNodeModel",
          "org.projectcontinuum.test.node.ParsePageNodeModel",
          "org.projectcontinuum.test.node.AppendRowsNodeModel",
          "org.projectcontinuum.test.node.WriteTableNodeModel"
        ),
        requested
      )
      assertEquals(setOf("seed", "fetch", "parse", "append", "write"), result.keys)
      assertTrue("L" !in env.fakeNodeActivity.invokedNodeIds, "the loop has no activity")
    }
  }

  @Test
  fun `members are dispatched without parentNode and collapsed`() {
    newEnv().use { env ->
      runFixture(env = env)

      memberIds.forEach { memberId ->
        val received = env.fakeNodeActivity.receivedNodes.getValue(memberId)
        assertNull(received.parentNode, "$memberId must not carry parentNode to the worker")
        assertNull(received.data.collapsed, "$memberId must not carry collapsed to the worker")
      }
      assertEquals(listOf("seed", "fetch", "parse", "append", "write"), env.fakeNodeActivity.invokedNodeIds.toList())
    }
  }

  @Test
  fun `progress ignores the loop node, never decreases and ends at 100`() {
    newEnv().use { env ->
      val events = captureStatusEvents()

      runFixture(env = env)

      val progressValues = events.map { event -> event.progressPercentage }
      progressValues.zipWithNext().forEach { (previous, next) ->
        assertTrue(next >= previous, "progress went down: $progressValues")
      }
      val finalEvent = events.last()
      assertEquals("FINISHED", finalEvent.workflowStatus)
      assertEquals(100, finalEvent.progressPercentage)
      assertEquals(ContinuumWorkflowModel.NodeStatus.SUCCESS, finalEvent.loopStatus)
    }
  }
}
