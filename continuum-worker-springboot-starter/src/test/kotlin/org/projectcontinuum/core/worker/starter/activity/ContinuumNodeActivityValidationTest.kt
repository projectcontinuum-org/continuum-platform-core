package org.projectcontinuum.core.worker.starter.activity

import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.testing.TestActivityEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.annotation.ContinuumNode
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.node.ProcessNodeModel
import org.projectcontinuum.core.commons.node.TriggerNodeModel
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.core.worker.starter.credential.CredentialFetcherService
import org.springframework.context.ApplicationContext
import software.amazon.awssdk.transfer.s3.S3TransferManager
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Invalid node properties end the activity with a failed node output on the first attempt; Temporal never retries it. */
class ContinuumNodeActivityValidationTest {

  companion object {
    private val SCHEMA = mapOf(
      "type" to "object",
      "properties" to mapOf("count" to mapOf("type" to "integer")),
      "required" to listOf("count")
    )
  }

  @ContinuumNode
  class CountingTrigger : TriggerNodeModel() {
    val executions = AtomicInteger()
    override val outputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val categories = listOf("Test")
    override val metadata = ContinuumWorkflowModel.NodeData(
      description = "test", title = "Counting", nodeModel = CountingTrigger::class.java.name,
      properties = mapOf("count" to 1), propertiesSchema = SCHEMA
    )

    override fun execute(properties: Map<String, Any>?, nodeOutputWriter: NodeOutputWriter) {
      executions.incrementAndGet()
    }
  }

  @ContinuumNode
  class CountingProcess : ProcessNodeModel() {
    override val inputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val outputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val categories = listOf("Test")
    override val metadata = ContinuumWorkflowModel.NodeData(
      description = "test", title = "Counting", nodeModel = CountingProcess::class.java.name,
      properties = mapOf("count" to 1), propertiesSchema = SCHEMA
    )
  }

  /**
   * Counts activity attempts, so a Temporal retry would show up, and keeps the last output: the test environment
   * cannot deserialize [IContinuumNodeActivity.NodeActivityOutput] back into a reply (no Kotlin module).
   */
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

  private lateinit var testEnv: TestActivityEnvironment
  private lateinit var counting: Counting
  private val trigger = CountingTrigger()
  private val process = CountingProcess()

  @BeforeEach
  fun setUp() {
    val applicationContext = mock<ApplicationContext> {
      on { getBeanNamesForType(ProcessNodeModel::class.java) } doReturn arrayOf("process")
      on { getBeanNamesForType(TriggerNodeModel::class.java) } doReturn arrayOf("trigger")
      on { getType("process") } doReturn CountingProcess::class.java
      on { getType("trigger") } doReturn CountingTrigger::class.java
      on { getBean(CountingProcess::class.java) } doReturn process
      on { getBean(CountingTrigger::class.java) } doReturn trigger
    }
    val activity = ContinuumNodeActivity(
      applicationContext = applicationContext,
      s3TransferManager = mock<S3TransferManager>(),
      credentialFetcherService = mock<CredentialFetcherService>(),
      cacheBucketName = "bucket",
      cacheBucketBasePath = "base",
      cacheStoragePath = cacheDir,
      progressReportRateLimitMs = 5000,
      heartbeatIntervalMs = 60_000
    ).apply { onInit() }
    counting = Counting(activity)

    testEnv = TestActivityEnvironment.newInstance()
    testEnv.registerActivitiesImplementations(counting)
  }

  @AfterEach
  fun tearDown() {
    testEnv.close()
  }

  private fun stub(): IContinuumNodeActivity = testEnv.newActivityStub(
    IContinuumNodeActivity::class.java,
    ActivityOptions.newBuilder()
      .setStartToCloseTimeout(Duration.ofMinutes(1))
      // Same retry budget as the orchestration default: a retriable failure would be attempted again.
      .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(500).setInitialInterval(Duration.ofMillis(1)).build())
      .build()
  )

  private fun workflowNode(data: ContinuumWorkflowModel.NodeData, properties: Map<String, Any>) = ContinuumWorkflowModel.Node(
    id = "n1", type = "default", position = ContinuumWorkflowModel.Position(0.0, 0.0),
    data = data.copy(properties = properties), width = 1, height = 1, selected = false
  )

  /** Runs [node] through Temporal and returns the output the activity produced. */
  private fun runActivity(node: ContinuumWorkflowModel.Node): IContinuumNodeActivity.NodeActivityOutput {
    runCatching { stub().run(node, emptyMap()) }
    val output = counting.lastOutput.get()
    assertNotNull(output, "the activity threw instead of returning a node output")
    return output!!
  }

  private fun assertFailedOnce(output: IContinuumNodeActivity.NodeActivityOutput) {
    val error = output.outputs["\$error"]
    assertEquals(PortDataStatus.FAILED, error?.status, "outputs: ${output.outputs}")
    assertTrue(error!!.data.toString().contains("/count"), error.data.toString())
    assertEquals(1, counting.attempts.get(), "the activity was retried")
  }

  @Test
  fun `trigger node with invalid properties fails the node once and is not retried`() {
    val output = runActivity(workflowNode(trigger.metadata, mapOf("count" to "many")))

    assertFailedOnce(output)
    assertEquals(0, trigger.executions.get())
  }

  @Test
  fun `process node with invalid properties fails the node once and is not retried`() {
    val output = runActivity(workflowNode(process.metadata, mapOf("count" to 1.5)))

    assertFailedOnce(output)
  }

  @Test
  fun `an empty schema sent with the workflow does not skip the node's own validation`() {
    val spoofed = workflowNode(trigger.metadata.copy(propertiesSchema = emptyMap()), mapOf("count" to "many"))

    assertFailedOnce(runActivity(spoofed))
    assertEquals(0, trigger.executions.get())
  }
}
