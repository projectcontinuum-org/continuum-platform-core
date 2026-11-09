package org.projectcontinuum.core.worker.starter.activity

import io.temporal.activity.ActivityOptions
import io.temporal.client.ActivityCanceledException
import io.temporal.testing.TestActivityEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.annotation.ContinuumNode
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.node.ProcessNodeModel
import org.projectcontinuum.core.commons.node.TriggerNodeModel
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.core.worker.starter.credential.CredentialFetcherService
import org.springframework.context.ApplicationContext
import software.amazon.awssdk.transfer.s3.S3TransferManager
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** A cancelled activity must stop its node, even one that never reports progress or swallows the interrupt. */
class ContinuumNodeActivityCancellationTest {

  /** Busy for up to [RUN_FOR_MS] without calling report(); swallows interrupts but records them. */
  @ContinuumNode
  class StubbornNode : ProcessNodeModel() {
    val started = CountDownLatch(1)
    val sawInterrupt = AtomicBoolean(false)

    override val inputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val outputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val categories = listOf("Test")
    override val metadata = ContinuumWorkflowModel.NodeData(
      description = "test", title = "Stubborn", nodeModel = StubbornNode::class.java.name
    )

    override fun execute(
      properties: Map<String, Any>?,
      inputs: Map<String, NodeInputReader>,
      nodeOutputWriter: NodeOutputWriter,
      nodeProgressCallback: NodeProgressCallback
    ) {
      started.countDown()
      val until = System.currentTimeMillis() + RUN_FOR_MS
      while (System.currentTimeMillis() < until) {
        try {
          Thread.sleep(50)
        } catch (_: InterruptedException) {
          sawInterrupt.set(true)
          return
        }
      }
    }
  }

  /** Records what the activity throws; the test environment cannot turn a cancelled result into a reply (NPE). */
  class Recording(private val delegate: IContinuumNodeActivity) : IContinuumNodeActivity {
    val thrown = AtomicReference<Throwable?>()
    val activityThreadInterrupted = AtomicBoolean(false)

    override fun run(node: ContinuumWorkflowModel.Node, inputs: Map<String, PortData>): IContinuumNodeActivity.NodeActivityOutput =
      try {
        delegate.run(node, inputs)
      } catch (e: Throwable) {
        thrown.set(e)
        throw e
      } finally {
        activityThreadInterrupted.set(Thread.currentThread().isInterrupted)
      }
  }

  companion object {
    const val RUN_FOR_MS = 20_000L
  }

  @TempDir
  lateinit var cacheDir: Path

  private lateinit var testEnv: TestActivityEnvironment
  private val node = StubbornNode()
  private lateinit var recording: Recording

  @BeforeEach
  fun setUp() {
    val applicationContext = mock<ApplicationContext> {
      on { getBeanNamesForType(ProcessNodeModel::class.java) } doReturn arrayOf("stubborn")
      on { getBeanNamesForType(TriggerNodeModel::class.java) } doReturn emptyArray()
      on { getType("stubborn") } doReturn StubbornNode::class.java
      on { getBean(StubbornNode::class.java) } doReturn node
    }
    val activity = ContinuumNodeActivity(
      applicationContext = applicationContext,
      s3TransferManager = mock<S3TransferManager>(),
      credentialFetcherService = mock<CredentialFetcherService>(),
      cacheBucketName = "bucket",
      cacheBucketBasePath = "base",
      cacheStoragePath = cacheDir,
      progressReportRateLimitMs = 5000,
      heartbeatIntervalMs = 100
    ).apply { onInit() }
    recording = Recording(activity)

    testEnv = TestActivityEnvironment.newInstance()
    testEnv.registerActivitiesImplementations(recording)
  }

  @AfterEach
  fun tearDown() {
    testEnv.close()
  }

  @Test
  fun `cancellation seen by the background heartbeat interrupts the node and fails the activity as cancelled`() {
    val stub = testEnv.newActivityStub(
      IContinuumNodeActivity::class.java,
      ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofMinutes(1))
        .setHeartbeatTimeout(Duration.ofSeconds(5))
        .build()
    )
    val workflowNode = ContinuumWorkflowModel.Node(
      id = "n1", type = "default", position = ContinuumWorkflowModel.Position(0.0, 0.0),
      data = node.metadata, width = 1, height = 1, selected = false
    )

    Thread {
      if (node.started.await(10, TimeUnit.SECONDS)) testEnv.requestCancelActivity()
    }.start()

    val startedAt = System.currentTimeMillis()
    assertThrows<Exception> { stub.run(workflowNode, emptyMap()) }
    val elapsed = System.currentTimeMillis() - startedAt

    assertTrue(node.sawInterrupt.get(), "node thread was not interrupted")
    // the node returned normally after swallowing the interrupt; the activity still reports the cancellation
    assertTrue(recording.thrown.get() is ActivityCanceledException, "expected ActivityCanceledException, got ${recording.thrown.get()}")
    assertTrue(elapsed < RUN_FOR_MS / 2, "activity took ${elapsed}ms; the node was not stopped")
    // the interrupt sent to stop the node does not leak into the next task on the worker thread
    assertFalse(recording.activityThreadInterrupted.get())
  }
}
