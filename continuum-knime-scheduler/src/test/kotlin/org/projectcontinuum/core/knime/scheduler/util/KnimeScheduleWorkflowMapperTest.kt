package org.projectcontinuum.core.knime.scheduler.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import java.util.UUID

class KnimeScheduleWorkflowMapperTest {

  @Test
  fun `toWorkflowModel builds a single-node DAG pointing at the given content URL`() {
    val knimeWorkflowId = UUID.randomUUID()
    val contentUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/content"
    val executionUploadUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/executions"

    val model = KnimeScheduleWorkflowMapper.toWorkflowModel(
      name = "Nightly run",
      knimeWorkflowId = knimeWorkflowId,
      contentUrl = contentUrl,
      executionUploadUrl = executionUploadUrl,
      resetWorkflow = true,
      timeoutSeconds = 600
    )

    assertEquals(1, model.nodes.size)
    val node = model.nodes[0]
    assertEquals(KnimeScheduleWorkflowMapper.KNIME_EXECUTOR_NODE_MODEL, node.data.nodeModel)
    assertEquals(contentUrl, node.data.properties["workflowLocation"])
    assertEquals(executionUploadUrl, node.data.properties["executionUploadUrl"])
    assertEquals(600L, node.data.properties["timeoutSeconds"])
    assertEquals(true, node.data.properties["resetWorkflow"])
  }

  @Test
  fun `toWorkflowModel produces a node the workflow editor can actually render`() {
    val knimeWorkflowId = UUID.randomUUID()

    val model = KnimeScheduleWorkflowMapper.toWorkflowModel(
      name = "Nightly run",
      knimeWorkflowId = knimeWorkflowId,
      contentUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/content",
      executionUploadUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/executions",
      resetWorkflow = false,
      timeoutSeconds = 300
    )

    val node = model.nodes[0]
    // React Flow's nodeTypes map only recognizes "BaseNode" — anything else falls back to the
    // unstyled default node, which is what made this render incorrectly in the UI.
    assertEquals("BaseNode", node.type)
    assertTrue(node.data.inputs!!.isEmpty())
    assertTrue(node.data.outputs!!.containsKey("destinationPath"))
    assertEquals("text/plain", node.data.outputs!!["destinationPath"]?.contentType)
    assertFalse(node.data.icon.isNullOrBlank())
    assertFalse(node.data.subTitle.isNullOrBlank())
    assertTrue(node.data.propertiesUISchema.isNotEmpty())
  }

  @Test
  fun `isKnimeExecutorWorkflow is true only when a node uses the KNIME executor node model`() {
    val knimeWorkflowId = UUID.randomUUID()
    val knimeModel = KnimeScheduleWorkflowMapper.toWorkflowModel(
      name = "Nightly run",
      knimeWorkflowId = knimeWorkflowId,
      contentUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/content",
      executionUploadUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/executions",
      resetWorkflow = false,
      timeoutSeconds = 300
    )
    val genericModel = ContinuumWorkflowModel(id = "wf-2", name = "Generic workflow", nodes = emptyList())

    assertTrue(KnimeScheduleWorkflowMapper.isKnimeExecutorWorkflow(knimeModel))
    assertFalse(KnimeScheduleWorkflowMapper.isKnimeExecutorWorkflow(genericModel))
  }

  @Test
  fun `extractKnimeWorkflowId parses the workflow id back out of the content URL`() {
    val knimeWorkflowId = UUID.randomUUID()
    val model = KnimeScheduleWorkflowMapper.toWorkflowModel(
      name = "Nightly run",
      knimeWorkflowId = knimeWorkflowId,
      contentUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/content",
      executionUploadUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/executions",
      resetWorkflow = false,
      timeoutSeconds = 300
    )

    assertEquals(knimeWorkflowId, KnimeScheduleWorkflowMapper.extractKnimeWorkflowId(model))
  }

  @Test
  fun `extractKnimeWorkflowId returns null for a non-KNIME workflow`() {
    val genericModel = ContinuumWorkflowModel(id = "wf-2", name = "Generic workflow", nodes = emptyList())

    assertNull(KnimeScheduleWorkflowMapper.extractKnimeWorkflowId(genericModel))
  }

  @Test
  fun `extractResetWorkflow and extractTimeoutSeconds read back the node properties`() {
    val knimeWorkflowId = UUID.randomUUID()
    val model = KnimeScheduleWorkflowMapper.toWorkflowModel(
      name = "Nightly run",
      knimeWorkflowId = knimeWorkflowId,
      contentUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/content",
      executionUploadUrl = "http://localhost:8085/api/v1/knime-workflows/$knimeWorkflowId/executions",
      resetWorkflow = true,
      timeoutSeconds = 900
    )

    assertTrue(KnimeScheduleWorkflowMapper.extractResetWorkflow(model))
    assertEquals(900L, KnimeScheduleWorkflowMapper.extractTimeoutSeconds(model))
  }

  @Test
  fun `extractResetWorkflow and extractTimeoutSeconds fall back to defaults for a non-KNIME workflow`() {
    val genericModel = ContinuumWorkflowModel(id = "wf-2", name = "Generic workflow", nodes = emptyList())

    assertFalse(KnimeScheduleWorkflowMapper.extractResetWorkflow(genericModel))
    assertEquals(300L, KnimeScheduleWorkflowMapper.extractTimeoutSeconds(genericModel))
  }
}
