package org.projectcontinuum.core.worker.starter.activity

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.projectcontinuum.core.commons.annotation.ContinuumNode
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.node.ProcessNodeModel
import org.projectcontinuum.core.commons.node.TriggerNodeModel
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.core.worker.starter.credential.CredentialFetcherService
import org.springframework.context.ApplicationContext
import software.amazon.awssdk.transfer.s3.S3TransferManager
import java.nio.file.Path

/** Startup fails when a node declares a port id that starts with the reserved system prefix `$`. */
class ContinuumNodeActivityReservedPortTest {

  companion object {
    private val TABLE_PORT = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")
  }

  @ContinuumNode
  class ConfigurableProcessNode(
    override val inputPorts: Map<String, ContinuumWorkflowModel.NodePort> = emptyMap(),
    override val outputPorts: Map<String, ContinuumWorkflowModel.NodePort> = emptyMap(),
    metadataInputs: Map<String, ContinuumWorkflowModel.NodePort>? = null,
    metadataOutputs: Map<String, ContinuumWorkflowModel.NodePort>? = null
  ) : ProcessNodeModel() {
    override val categories = listOf("Test")
    override val metadata = ContinuumWorkflowModel.NodeData(
      description = "test", title = "Process", nodeModel = ConfigurableProcessNode::class.java.name,
      inputs = metadataInputs, outputs = metadataOutputs
    )
  }

  @ContinuumNode
  class ConfigurableTriggerNode(
    override val outputPorts: Map<String, ContinuumWorkflowModel.NodePort> = emptyMap(),
    metadataOutputs: Map<String, ContinuumWorkflowModel.NodePort>? = null
  ) : TriggerNodeModel() {
    override val categories = listOf("Test")
    override val metadata = ContinuumWorkflowModel.NodeData(
      description = "test", title = "Trigger", nodeModel = ConfigurableTriggerNode::class.java.name,
      outputs = metadataOutputs
    )

    override fun execute(properties: Map<String, Any>?, nodeOutputWriter: NodeOutputWriter) {}
  }

  private fun initActivity(
    processNode: ConfigurableProcessNode = ConfigurableProcessNode(),
    triggerNode: ConfigurableTriggerNode = ConfigurableTriggerNode()
  ) {
    val applicationContext = mock<ApplicationContext> {
      on { getBeanNamesForType(ProcessNodeModel::class.java) } doReturn arrayOf("process")
      on { getBeanNamesForType(TriggerNodeModel::class.java) } doReturn arrayOf("trigger")
      on { getType("process") } doReturn ConfigurableProcessNode::class.java
      on { getType("trigger") } doReturn ConfigurableTriggerNode::class.java
      on { getBean(ConfigurableProcessNode::class.java) } doReturn processNode
      on { getBean(ConfigurableTriggerNode::class.java) } doReturn triggerNode
    }
    ContinuumNodeActivity(
      applicationContext = applicationContext,
      s3TransferManager = mock<S3TransferManager>(),
      credentialFetcherService = mock<CredentialFetcherService>(),
      cacheBucketName = "bucket",
      cacheBucketBasePath = "base",
      cacheStoragePath = Path.of("build/tmp/reserved-port-test"),
      progressReportRateLimitMs = 5000,
      heartbeatIntervalMs = 60_000
    ).onInit()
  }

  private fun assertStartupFailsFor(reservedPortId: String, initialize: () -> Unit) {
    val exception = assertThrows<IllegalStateException> { initialize() }
    assertTrue(exception.message!!.contains(reservedPortId), exception.message)
    assertTrue(exception.message!!.contains("reserved for system keys"), exception.message)
  }

  @Test
  fun `nodes with ordinary port ids start`() {
    assertDoesNotThrow {
      initActivity(
        processNode = ConfigurableProcessNode(inputPorts = mapOf("in" to TABLE_PORT), outputPorts = mapOf("out" to TABLE_PORT)),
        triggerNode = ConfigurableTriggerNode(outputPorts = mapOf("out" to TABLE_PORT))
      )
    }
  }

  @Test
  fun `a process node input port starting with the reserved prefix fails startup`() {
    assertStartupFailsFor(reservedPortId = "\$in") {
      initActivity(processNode = ConfigurableProcessNode(inputPorts = mapOf("\$in" to TABLE_PORT)))
    }
  }

  @Test
  fun `a process node output port starting with the reserved prefix fails startup`() {
    assertStartupFailsFor(reservedPortId = "\$variable") {
      initActivity(processNode = ConfigurableProcessNode(outputPorts = mapOf("\$variable" to TABLE_PORT)))
    }
  }

  @Test
  fun `reserved port ids in the process node metadata fail startup`() {
    assertStartupFailsFor(reservedPortId = "\$meta-in") {
      initActivity(processNode = ConfigurableProcessNode(metadataInputs = mapOf("\$meta-in" to TABLE_PORT)))
    }
    assertStartupFailsFor(reservedPortId = "\$meta-out") {
      initActivity(processNode = ConfigurableProcessNode(metadataOutputs = mapOf("\$meta-out" to TABLE_PORT)))
    }
  }

  @Test
  fun `a trigger node output port starting with the reserved prefix fails startup`() {
    assertStartupFailsFor(reservedPortId = "\$error") {
      initActivity(triggerNode = ConfigurableTriggerNode(outputPorts = mapOf("\$error" to TABLE_PORT)))
    }
    assertStartupFailsFor(reservedPortId = "\$meta-out") {
      initActivity(triggerNode = ConfigurableTriggerNode(metadataOutputs = mapOf("\$meta-out" to TABLE_PORT)))
    }
  }
}
