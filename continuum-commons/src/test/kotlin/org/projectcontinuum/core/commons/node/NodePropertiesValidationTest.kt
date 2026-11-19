package org.projectcontinuum.core.commons.node

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.projectcontinuum.core.commons.context.ExecutionContext
import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.exception.PropertiesValidationException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.protocol.progress.NodeProgress
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import java.nio.file.Path

/** Process and trigger nodes refuse invalid properties before execute(), as a non-retriable node error. */
class NodePropertiesValidationTest {

  @TempDir
  lateinit var tempDir: Path

  private val schema = mapOf(
    "type" to "object",
    "properties" to mapOf("count" to mapOf("type" to "integer", "minimum" to 1)),
    "required" to listOf("count")
  )

  private fun metadata(nodeModel: String) = ContinuumWorkflowModel.NodeData(
    description = "test", title = "Test", nodeModel = nodeModel, properties = mapOf("count" to 1), propertiesSchema = schema
  )

  private class RecordingProcessNode(override val metadata: ContinuumWorkflowModel.NodeData) : ProcessNodeModel() {
    var executed = false
    override val inputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val outputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val categories = listOf("Test")

    override fun execute(
      properties: Map<String, Any>?,
      inputs: Map<String, NodeInputReader>,
      nodeOutputWriter: NodeOutputWriter,
      nodeProgressCallback: NodeProgressCallback
    ) {
      executed = true
    }
  }

  private class RecordingTriggerNode(override val metadata: ContinuumWorkflowModel.NodeData) : TriggerNodeModel() {
    var executed = false
    override val outputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()
    override val categories = listOf("Test")

    override fun execute(properties: Map<String, Any>?, nodeOutputWriter: NodeOutputWriter) {
      executed = true
    }
  }

  private val noProgress = object : NodeProgressCallback {
    override fun report(nodeProgress: NodeProgress) {}
    override fun report(progressPercentage: Int) {}
  }

  private fun workflowNode(data: ContinuumWorkflowModel.NodeData, properties: Map<String, Any>) = ContinuumWorkflowModel.Node(
    id = "n1", type = "default", position = ContinuumWorkflowModel.Position(0.0, 0.0),
    data = data.copy(properties = properties), width = 1, height = 1, selected = false
  )

  private fun assertNonRetriableValidationError(e: NodeRuntimeException) {
    assertFalse(e.isRetriable, "a validation failure must not be retried")
    assertEquals("n1", e.nodeId)
    assertTrue(e.message.contains("/count"), e.message)
    assertTrue(e.cause is PropertiesValidationException, "cause was ${e.cause}")
  }

  @Test
  fun `process node with invalid properties fails non-retriable and never executes`() {
    val node = RecordingProcessNode(metadata("Process"))

    val e = assertThrows<NodeRuntimeException> {
      node.run(workflowNode(node.metadata, mapOf("count" to 0)), emptyMap(), NodeOutputWriter(tempDir), noProgress, ExecutionContext.EMPTY)
    }

    assertNonRetriableValidationError(e)
    assertFalse(node.executed)
  }

  @Test
  fun `process node run without an execution context validates too`() {
    val node = RecordingProcessNode(metadata("Process"))

    assertThrows<NodeRuntimeException> {
      node.run(workflowNode(node.metadata, mapOf("count" to "one")), emptyMap(), NodeOutputWriter(tempDir), noProgress)
    }
    assertFalse(node.executed)
  }

  @Test
  fun `the node's own schema is used, not the propertiesSchema sent with the workflow`() {
    val process = RecordingProcessNode(metadata("Process"))
    val trigger = RecordingTriggerNode(metadata("Trigger"))
    // A client that blanks the schema in the workflow must not skip validation.
    fun spoofed(data: ContinuumWorkflowModel.NodeData) =
      workflowNode(data, mapOf("count" to "many")).let { it.copy(data = it.data.copy(propertiesSchema = emptyMap())) }

    assertThrows<NodeRuntimeException> {
      process.run(spoofed(process.metadata), emptyMap(), NodeOutputWriter(tempDir), noProgress, ExecutionContext.EMPTY)
    }
    assertThrows<NodeRuntimeException> { trigger.run(spoofed(trigger.metadata), NodeOutputWriter(tempDir)) }
    assertFalse(process.executed)
    assertFalse(trigger.executed)
  }

  @Test
  fun `process node with valid properties executes`() {
    val node = RecordingProcessNode(metadata("Process"))

    node.run(workflowNode(node.metadata, mapOf("count" to 2)), emptyMap(), NodeOutputWriter(tempDir), noProgress, ExecutionContext.EMPTY)

    assertTrue(node.executed)
  }

  @Test
  fun `trigger node with invalid properties fails non-retriable and never executes`() {
    val node = RecordingTriggerNode(metadata("Trigger"))

    val e = assertThrows<NodeRuntimeException> { node.run(workflowNode(node.metadata, mapOf("count" to "many")), NodeOutputWriter(tempDir)) }

    assertNonRetriableValidationError(e)
    assertFalse(node.executed)
  }

  @Test
  fun `trigger node missing a required property fails non-retriable`() {
    val node = RecordingTriggerNode(metadata("Trigger"))

    val e = assertThrows<NodeRuntimeException> { node.run(workflowNode(node.metadata, emptyMap()), NodeOutputWriter(tempDir)) }

    assertFalse(e.isRetriable)
    assertTrue(e.message.contains("required property 'count'"), e.message)
    assertFalse(node.executed)
  }

  @Test
  fun `trigger node with valid properties executes`() {
    val node = RecordingTriggerNode(metadata("Trigger"))

    node.run(workflowNode(node.metadata, mapOf("count" to 5)), NodeOutputWriter(tempDir))

    assertTrue(node.executed)
  }
}
