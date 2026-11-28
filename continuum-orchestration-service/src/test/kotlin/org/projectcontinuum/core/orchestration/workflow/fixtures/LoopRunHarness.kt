package org.projectcontinuum.core.orchestration.workflow.fixtures

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.api.enums.v1.IndexedValueType
import io.temporal.api.history.v1.History
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowOptions
import io.temporal.common.WorkflowExecutionHistory
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.worker.WorkerOptions
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.model.WorkflowUpdateEvent
import org.projectcontinuum.core.commons.validation.loop.LoopConditionEvaluator
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import org.projectcontinuum.core.orchestration.utils.StatusHelper
import org.projectcontinuum.core.orchestration.workflow.ContinuumWorkflow
import org.projectcontinuum.core.orchestration.workflow.loop.ConditionEvaluationRunner
import org.projectcontinuum.core.orchestration.workflow.loop.LoopConditionEvaluators
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.messaging.Message
import java.io.Closeable
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A Temporal test environment running [ContinuumWorkflow] with a [FakeContinuumNodeActivity] and an installed loop
 * condition evaluator, for the loop execution tests. Captures every published status event. Close it after the test:
 * it also removes the evaluator and the status publisher.
 *
 * @param runner Runs the condition evaluations (default: on the workflow thread, as the loop logic tests need).
 * @param deadlockDetectionTimeoutMillis The workflow worker's deadlock detection timeout; null keeps Temporal's one
 *   second. A lower value lets a test show on any machine that a check never blocks the workflow thread for long.
 * @param workflowTaskTimeout The runs' workflow task timeout (Temporal's default 10 s). The test server sometimes
 *   numbers a fresh workflow task as attempt 2, and the SDK reports a failure of a later attempt by letting the task
 *   time out, so tests of workflow-task retries shorten it to stay fast.
 */
class LoopRunHarness(
  evaluator: LoopConditionEvaluator,
  runner: ConditionEvaluationRunner = ConditionEvaluationRunner.DIRECT,
  deadlockDetectionTimeoutMillis: Long? = null,
  private val workflowTaskTimeout: Duration = Duration.ofSeconds(10)
) : Closeable {

  private val activityTaskQueue = "TEST_LOOP_RUN_NODE_ACTIVITY_TASK_QUEUE"

  val testEnvironment: TestWorkflowEnvironment
  val client: WorkflowClient
  val fakeNodeActivity = FakeContinuumNodeActivity()
  val fakeInitializeActivity = FakeInitializeActivity(taskQueueForAllNodes = activityTaskQueue)

  /** Every published status event, in order, as it was when it was sent. */
  val events: MutableList<WorkflowUpdateEvent> = CopyOnWriteArrayList()

  companion object {
    /** A new, not yet started environment with the orchestrator's data converter (Kotlin model classes). */
    fun newEnvironment(): TestWorkflowEnvironment {
      val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper().apply { registerModule(KotlinModule.Builder().build()) }
      val dataConverter = DefaultDataConverter.newDefaultInstance().withPayloadConverterOverrides(JacksonJsonPayloadConverter(mapper))
      return TestWorkflowEnvironment.newInstance(
        TestEnvironmentOptions.newBuilder()
          .setWorkflowClientOptions(WorkflowClientOptions.newBuilder().setDataConverter(dataConverter).build())
          .registerSearchAttribute("Continuum:ExecutionStatus", IndexedValueType.INDEXED_VALUE_TYPE_INT)
          .registerSearchAttribute("Continuum:WorkflowFileName", IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD)
          .build()
      )
    }
  }

  init {
    LoopConditionEvaluators.install(evaluator = evaluator, runner = runner)
    testEnvironment = newEnvironment()
    val workflowWorkerOptions = WorkerOptions.newBuilder()
      .apply { deadlockDetectionTimeoutMillis?.let { timeoutMillis -> setDefaultDeadlockDetectionTimeout(timeoutMillis) } }
      .build()
    testEnvironment.newWorker(TaskQueues.WORKFLOW_TASK_QUEUE, workflowWorkerOptions)
      .registerWorkflowImplementationTypes(ContinuumWorkflow::class.java)
    testEnvironment.newWorker(activityTaskQueue).registerActivitiesImplementations(fakeNodeActivity)
    testEnvironment.newWorker(TaskQueues.ACTIVITY_TASK_QUEUE_INITIALIZE).registerActivitiesImplementations(fakeInitializeActivity)
    testEnvironment.start()
    client = testEnvironment.workflowClient
    val bridge = mock<StreamBridge>()
    whenever(bridge.send(any<String>(), any<Any>())).thenAnswer { invocation ->
      // The event holds the live workflow model, so copy it now: later passes change statuses in place.
      val liveEvent = invocation.getArgument<Message<*>>(1).payload as WorkflowUpdateEvent
      events.add(WorkflowFixtures.objectMapper.readValue(WorkflowFixtures.objectMapper.writeValueAsString(liveEvent), WorkflowUpdateEvent::class.java))
      true
    }
    StatusHelper(bridge).postConstruct()
  }

  /** The outcome of a run: its id, the node outputs it returned (null when it failed) and the failure. */
  class RunResult(val workflowId: String, val outputs: Map<String, Map<String, PortData>>?, val failure: Throwable?) {
    val succeeded: Boolean get() = failure == null
  }

  fun samplePortData(value: String) =
    PortData(status = PortDataStatus.SUCCESS, contentType = "text/plain", tableSpec = emptyList(), data = value)

  /** A run that was started and may still be running; [await] waits for its end. */
  class StartedRun(val workflowId: String, private val future: CompletableFuture<Map<String, Map<String, PortData>>>) {
    fun await(): RunResult =
      try {
        RunResult(workflowId = workflowId, outputs = future.get(30, TimeUnit.SECONDS), failure = null)
      } catch (failure: java.util.concurrent.ExecutionException) {
        RunResult(workflowId = workflowId, outputs = null, failure = failure.cause)
      }
  }

  /** Every non-loop node of [workflow] returns one `out` port, then the workflow runs to its end. */
  fun run(workflow: ContinuumWorkflowModel): RunResult = start(workflow = workflow).await()

  /** Starts [workflow] like [run] does and returns at once, so a test can have several runs in flight. */
  fun start(workflow: ContinuumWorkflowModel): StartedRun {
    workflow.nodes.forEach { node ->
      fakeNodeActivity.successOutputs.putIfAbsent(node.id, mapOf("out" to samplePortData(value = node.id)))
    }
    val workflowId = "loop-run-${UUID.randomUUID()}"
    val stub = client.newWorkflowStub(
      IContinuumWorkflow::class.java,
      WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE)
        .setWorkflowTaskTimeout(workflowTaskTimeout)
        .build()
    )
    val future: CompletableFuture<Map<String, Map<String, PortData>>> = WorkflowClient.execute(stub::start, workflow)
    return StartedRun(workflowId = workflowId, future = future)
  }

  fun history(workflowId: String): History = client.fetchHistory(workflowId).history

  /** The run's history in the form [io.temporal.testing.WorkflowReplayer] replays. */
  fun executionHistory(workflowId: String): WorkflowExecutionHistory = client.fetchHistory(workflowId)

  /** Replays [history] with [ContinuumWorkflow] on a fresh environment with the orchestrator's data converter. */
  fun replay(history: WorkflowExecutionHistory) {
    newEnvironment().use { replayEnvironment ->
      io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history, replayEnvironment, ContinuumWorkflow::class.java)
    }
  }

  /** The node as published in the last status event. */
  fun lastPublished(nodeId: String): ContinuumWorkflowModel.Node =
    events.last().data.workflow.nodes.single { node -> node.id == nodeId }

  /** The `$error` text of [nodeId] in the last status event (errors are published with the outputs). */
  fun lastError(nodeId: String): String? = lastOutputs(nodeId = nodeId)?.get("\$error")?.data?.toString()

  /** The outputs (or errors) of [nodeId] in the last status event. */
  fun lastOutputs(nodeId: String): Map<String, PortData>? {
    val rawOutputs = events.last().data.nodeToOutputsMap[nodeId] ?: return null
    val portDataMapType = WorkflowFixtures.objectMapper.typeFactory
      .constructMapType(Map::class.java, String::class.java, PortData::class.java)
    return WorkflowFixtures.objectMapper.convertValue(rawOutputs, portDataMapType)
  }

  override fun close() {
    testEnvironment.close()
    StatusHelper.reset()
    LoopConditionEvaluators.reset()
  }
}
