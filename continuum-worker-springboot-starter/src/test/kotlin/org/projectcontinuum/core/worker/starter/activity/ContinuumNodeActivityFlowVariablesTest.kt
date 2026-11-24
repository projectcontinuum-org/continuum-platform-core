package org.projectcontinuum.core.worker.starter.activity

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.activity.ActivityOptions
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowOptions
import io.temporal.common.RetryOptions
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.workflow.Workflow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.annotation.ContinuumNode
import org.projectcontinuum.core.commons.context.ExecutionContext
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.model.WorkflowSnapshot
import org.projectcontinuum.core.commons.node.ProcessNodeModel
import org.projectcontinuum.core.commons.node.TriggerNodeModel
import org.projectcontinuum.core.commons.protocol.progress.ContinuumNodeActivitySignal
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.FlowVariables
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import org.projectcontinuum.core.worker.starter.credential.CredentialFetcherService
import org.springframework.context.ApplicationContext
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest
import software.amazon.awssdk.transfer.s3.model.FileUpload
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Flow variables at the activity level: `inputs["$variable"]` reaches the node through [ExecutionContext] and is never
 * downloaded; `outputs["$variable"]` carries only what the node set itself, within the configured size cap.
 */
class ContinuumNodeActivityFlowVariablesTest {

  /** Records the inherited flow variables it received and sets [variablesToSet]. */
  @ContinuumNode
  class VariableProcessNode : ProcessNodeModel() {
    val receivedFlowVariables = AtomicReference<Map<String, Any>?>()
    val receivedDataPortIds = AtomicReference<Set<String>?>()
    var variablesToSet: Map<String, Any> = emptyMap()
    var rowsToWrite: Map<String, Int> = emptyMap()

    override val inputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val outputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val categories = listOf("Test")
    override val metadata = ContinuumWorkflowModel.NodeData(
      description = "test", title = "Variables", nodeModel = VariableProcessNode::class.java.name
    )

    override fun execute(
      properties: Map<String, Any>?,
      inputs: Map<String, NodeInputReader>,
      nodeOutputWriter: NodeOutputWriter,
      nodeProgressCallback: NodeProgressCallback,
      executionContext: ExecutionContext
    ) {
      receivedFlowVariables.set(executionContext.inputFlowVariables)
      receivedDataPortIds.set(inputs.keys)
      executionContext.setFlowVariables(variables = variablesToSet)
      rowsToWrite.forEach { (portId, rowCount) ->
        nodeOutputWriter.createOutputPortWriter(portId).use { writer ->
          (0 until rowCount).forEach { rowNumber -> writer.write(rowNumber = rowNumber.toLong(), row = mapOf("n" to rowNumber)) }
        }
      }
    }
  }

  /** A trigger that emits initial flow variables. */
  @ContinuumNode
  class VariableTriggerNode : TriggerNodeModel() {
    override val outputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val categories = listOf("Test")
    override val metadata = ContinuumWorkflowModel.NodeData(
      description = "test", title = "Variable trigger", nodeModel = VariableTriggerNode::class.java.name
    )

    override fun execute(
      properties: Map<String, Any>?,
      nodeOutputWriter: NodeOutputWriter,
      executionContext: ExecutionContext
    ) {
      executionContext.setFlowVariable(name = "runLabel", value = "nightly")
      executionContext.setFlowVariable(name = "batch", value = 1)
    }
  }

  /**
   * Minimal parent workflow: runs the node activity once with [NEXT_INPUTS] and accepts the worker's progress signals
   * (an activity-only test environment cannot deliver them, and the worker signals 100% after every node).
   */
  class HostWorkflow : IContinuumWorkflow {
    override fun start(continuumWorkflow: ContinuumWorkflowModel): Map<String, Map<String, PortData>> {
      val activity = Workflow.newActivityStub(
        IContinuumNodeActivity::class.java,
        ActivityOptions.newBuilder()
          .setTaskQueue(ACTIVITY_TASK_QUEUE)
          .setStartToCloseTimeout(Duration.ofMinutes(1))
          .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).setInitialInterval(Duration.ofMillis(1)).build())
          .build()
      )
      runCatching { activity.run(continuumWorkflow.nodes.single(), NEXT_INPUTS) }
      return emptyMap()
    }

    override fun getWorkflowSnapshot(): WorkflowSnapshot = WorkflowSnapshot(nodeToOutputsMap = emptyMap())
    override fun updateNodeProgressSignal(continuumNodeActivitySignal: ContinuumNodeActivitySignal) {}
    override fun notifyCancelling(reason: String?) {}
    override fun notifyTerminating(reason: String?) {}
  }

  companion object {
    private const val WORKFLOW_TASK_QUEUE = "FLOW_VARIABLES_TEST_WORKFLOW_QUEUE"
    private const val ACTIVITY_TASK_QUEUE = "FLOW_VARIABLES_TEST_ACTIVITY_QUEUE"

    /** Inputs the [HostWorkflow] passes to the activity; set by the test before starting it (same JVM). */
    @Volatile
    var NEXT_INPUTS: Map<String, PortData> = emptyMap()
  }

  /** Counts attempts and keeps the last output the activity returned. */
  class Counting(private val delegate: IContinuumNodeActivity) : IContinuumNodeActivity {
    val attempts = AtomicInteger()
    val lastOutput = AtomicReference<IContinuumNodeActivity.NodeActivityOutput?>()
    override fun run(node: ContinuumWorkflowModel.Node, inputs: Map<String, PortData>): IContinuumNodeActivity.NodeActivityOutput {
      attempts.incrementAndGet()
      return delegate.run(node, inputs).also { lastOutput.set(it) }
    }
  }

  @TempDir
  lateinit var cacheDir: Path

  /** Uploads complete at once, so a node that writes output ports can finish. */
  private val completedUpload: FileUpload = mock<FileUpload>().also { upload ->
    whenever(upload.completionFuture()).thenReturn(CompletableFuture.completedFuture(mock<CompletedFileUpload>()))
  }
  private val s3TransferManager: S3TransferManager = mock<S3TransferManager>().also { transferManager ->
    whenever(transferManager.uploadFile(any<UploadFileRequest>())).thenReturn(completedUpload)
  }
  private val processNode = VariableProcessNode()
  private val triggerNode = VariableTriggerNode()
  private var testEnv: TestWorkflowEnvironment? = null
  private lateinit var counting: Counting

  private fun startActivity(maxFlowVariablesSizeBytes: Long = ContinuumNodeActivity.DEFAULT_MAX_FLOW_VARIABLES_SIZE_BYTES) {
    val applicationContext = mock<ApplicationContext> {
      on { getBeanNamesForType(ProcessNodeModel::class.java) } doReturn arrayOf("process")
      on { getBeanNamesForType(TriggerNodeModel::class.java) } doReturn arrayOf("trigger")
      on { getType("process") } doReturn VariableProcessNode::class.java
      on { getType("trigger") } doReturn VariableTriggerNode::class.java
      on { getBean(VariableProcessNode::class.java) } doReturn processNode
      on { getBean(VariableTriggerNode::class.java) } doReturn triggerNode
    }
    val activity = ContinuumNodeActivity(
      applicationContext = applicationContext,
      s3TransferManager = s3TransferManager,
      credentialFetcherService = mock<CredentialFetcherService>(),
      cacheBucketName = "bucket",
      cacheBucketBasePath = "base",
      cacheStoragePath = cacheDir,
      progressReportRateLimitMs = 5000,
      heartbeatIntervalMs = 60_000,
      maxFlowVariablesSizeBytes = maxFlowVariablesSizeBytes
    ).apply { onInit() }
    counting = Counting(activity)

    // Same Kotlin-aware JSON converter as production, so PortData round-trips like it does between services.
    val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper()
      .apply { registerModule(KotlinModule.Builder().build()) }
    val dataConverter = DefaultDataConverter.newDefaultInstance()
      .withPayloadConverterOverrides(JacksonJsonPayloadConverter(mapper))
    testEnv = TestWorkflowEnvironment.newInstance(
      TestEnvironmentOptions.newBuilder()
        .setWorkflowClientOptions(WorkflowClientOptions.newBuilder().setDataConverter(dataConverter).build())
        .build()
    ).apply {
      newWorker(WORKFLOW_TASK_QUEUE).registerWorkflowImplementationTypes(HostWorkflow::class.java)
      newWorker(ACTIVITY_TASK_QUEUE).registerActivitiesImplementations(counting)
      start()
    }
  }

  @AfterEach
  fun tearDown() {
    testEnv?.close()
  }

  private fun workflowNode(data: ContinuumWorkflowModel.NodeData) = ContinuumWorkflowModel.Node(
    id = "n1", type = "default", position = ContinuumWorkflowModel.Position(0.0, 0.0),
    data = data, width = 1, height = 1, selected = false
  )

  /** Runs [node] through Temporal (so inputs are serialized like in production) and returns the activity's output. */
  private fun runActivity(node: ContinuumWorkflowModel.Node, inputs: Map<String, PortData>): IContinuumNodeActivity.NodeActivityOutput {
    NEXT_INPUTS = inputs
    val workflow = testEnv!!.workflowClient.newWorkflowStub(
      IContinuumWorkflow::class.java,
      WorkflowOptions.newBuilder()
        .setWorkflowId("flow-variables-${UUID.randomUUID()}")
        .setTaskQueue(WORKFLOW_TASK_QUEUE)
        .build()
    )
    WorkflowClient.execute(workflow::start, ContinuumWorkflowModel(id = "wf", name = "wf", nodes = listOf(node)))
      .get(30, TimeUnit.SECONDS)
    val output = counting.lastOutput.get()
    assertNotNull(output, "the activity threw instead of returning a node output")
    return output!!
  }

  private fun variableInputs(variables: Map<String, Any>) =
    mapOf(NodeOutputSystemPort.VARIABLE.key to FlowVariables.toPortData(variables = variables))

  /** Default variable prefixes of node `n1` of the two test node types. */
  private val processPrefix = "variableProcessNode_n1"
  private val triggerPrefix = "variableTriggerNode_n1"

  /** The emitted flow variables without the defaults the framework adds under [prefix]. */
  private fun nodeSetVariables(outputs: Map<String, PortData>, prefix: String) =
    FlowVariables.extract(ports = outputs).filterKeys { name -> !name.startsWith("$prefix.") }

  /** The defaults the framework exported under [prefix]. */
  private fun defaultVariables(outputs: Map<String, PortData>, prefix: String) =
    FlowVariables.extract(ports = outputs).filterKeys { name -> name.startsWith("$prefix.") }

  @Test
  fun `inherited variables reach the node and the variable key is never downloaded`() {
    startActivity()

    runActivity(node = workflowNode(processNode.metadata), inputs = variableInputs(mapOf("x" to 1, "label" to "a")))

    assertEquals(mapOf("x" to 1, "label" to "a"), processNode.receivedFlowVariables.get())
    assertEquals(emptySet<String>(), processNode.receivedDataPortIds.get(), "system keys must not become data inputs")
    verify(s3TransferManager, never()).downloadFile(any<DownloadFileRequest>())
  }

  @Test
  fun `inherited variables are not passed back when the node sets none`() {
    startActivity()

    val output = runActivity(node = workflowNode(processNode.metadata), inputs = variableInputs(mapOf("x" to 1)))

    assertEquals(emptyMap<String, Any>(), nodeSetVariables(outputs = output.outputs, prefix = processPrefix))
  }

  @Test
  fun `a successful node exports its default variables even when it sets none`() {
    startActivity()

    val output = runActivity(node = workflowNode(processNode.metadata), inputs = emptyMap())

    val defaults = defaultVariables(outputs = output.outputs, prefix = processPrefix)
    assertEquals(
      setOf("executionStatus", "startTimestamp", "endTimestamp", "durationMs", "inputCount", "outputCount")
        .map { "$processPrefix.$it" }.toSet(),
      defaults.keys
    )
    assertEquals("SUCCESS", defaults["$processPrefix.executionStatus"])
    val start = (defaults.getValue("$processPrefix.startTimestamp") as Number).toLong()
    val end = (defaults.getValue("$processPrefix.endTimestamp") as Number).toLong()
    assertTrue(end >= start, "end $end before start $start")
    assertEquals(end - start, (defaults.getValue("$processPrefix.durationMs") as Number).toLong())
    assertEquals(emptyMap<String, Long>(), defaults["$processPrefix.inputCount"])
    assertEquals(emptyMap<String, Long>(), defaults["$processPrefix.outputCount"])
    assertEquals(setOf(NodeOutputSystemPort.VARIABLE.key), output.outputs.keys, "no data port appears")
  }

  @Test
  fun `output row counts are exported per port`() {
    startActivity()
    processNode.rowsToWrite = mapOf("left" to 3, "right" to 1)

    val output = runActivity(node = workflowNode(processNode.metadata), inputs = emptyMap())

    assertEquals(
      mapOf("left" to 3L, "right" to 1L),
      defaultVariables(outputs = output.outputs, prefix = processPrefix)["$processPrefix.outputCount"]
    )
  }

  @Test
  fun `outputs carry only the variables the node set itself`() {
    startActivity()
    processNode.variablesToSet = mapOf("x" to 10, "z" to 3)

    val output = runActivity(node = workflowNode(processNode.metadata), inputs = variableInputs(mapOf("x" to 1, "y" to 1)))

    val variablePort = output.outputs.getValue(NodeOutputSystemPort.VARIABLE.key)
    assertEquals(PortDataStatus.SUCCESS, variablePort.status)
    assertEquals("application/json", variablePort.contentType)
    assertEquals(mapOf("x" to 10, "z" to 3), nodeSetVariables(outputs = output.outputs, prefix = processPrefix))
  }

  @Test
  fun `variables over the size cap fail the node once without retry`() {
    startActivity(maxFlowVariablesSizeBytes = 16)
    processNode.variablesToSet = mapOf("payload" to "x".repeat(100))

    val output = runActivity(node = workflowNode(processNode.metadata), inputs = emptyMap())

    val error = output.outputs[NodeOutputSystemPort.ERROR.key]
    assertEquals(PortDataStatus.FAILED, error?.status, "outputs: ${output.outputs}")
    assertTrue(error!!.data.toString().contains("max 16"), error.data.toString())
    assertEquals(setOf(NodeOutputSystemPort.ERROR.key, NodeOutputSystemPort.VARIABLE.key), output.outputs.keys)
    assertEquals(1, counting.attempts.get(), "the activity was retried")

    // The rejected node-set variable is not passed on; the failure is described by the defaults.
    assertEquals(emptyMap<String, Any>(), nodeSetVariables(outputs = output.outputs, prefix = processPrefix))
    val defaults = defaultVariables(outputs = output.outputs, prefix = processPrefix)
    assertEquals("FAILED", defaults["$processPrefix.executionStatus"])
    assertEquals(error.data, defaults["$processPrefix.errorMessage"])
    assertFalse(defaults.containsKey("$processPrefix.outputCount"), "a failed node has no output counts")
  }

  @Test
  fun `the defaults are not counted against the size cap`() {
    startActivity(maxFlowVariablesSizeBytes = 16)

    val output = runActivity(node = workflowNode(processNode.metadata), inputs = emptyMap())

    assertFalse(output.outputs.containsKey(NodeOutputSystemPort.ERROR.key), "outputs: ${output.outputs}")
    assertEquals("SUCCESS", defaultVariables(outputs = output.outputs, prefix = processPrefix)["$processPrefix.executionStatus"])
  }

  @Test
  fun `large inherited variables do not count against the cap`() {
    startActivity(maxFlowVariablesSizeBytes = 16)
    processNode.variablesToSet = mapOf("a" to 1)

    val output = runActivity(
      node = workflowNode(processNode.metadata),
      inputs = variableInputs(mapOf("big" to "x".repeat(1000)))
    )

    assertEquals(mapOf("a" to 1), nodeSetVariables(outputs = output.outputs, prefix = processPrefix))
  }

  @Test
  fun `a trigger node emits initial flow variables`() {
    startActivity()

    val output = runActivity(node = workflowNode(triggerNode.metadata), inputs = emptyMap())

    assertEquals(mapOf("runLabel" to "nightly", "batch" to 1), nodeSetVariables(outputs = output.outputs, prefix = triggerPrefix))
    val defaults = defaultVariables(outputs = output.outputs, prefix = triggerPrefix)
    assertEquals("SUCCESS", defaults["$triggerPrefix.executionStatus"])
    assertFalse(defaults.containsKey("$triggerPrefix.inputCount"), "a trigger node reads no inputs")
  }
}
