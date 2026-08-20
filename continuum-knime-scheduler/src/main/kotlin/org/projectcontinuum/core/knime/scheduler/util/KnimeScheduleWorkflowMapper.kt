package org.projectcontinuum.core.knime.scheduler.util

import org.projectcontinuum.core.knime.scheduler.client.Position
import org.projectcontinuum.core.knime.scheduler.client.WorkflowModel
import org.projectcontinuum.core.knime.scheduler.client.WorkflowNode
import org.projectcontinuum.core.knime.scheduler.client.WorkflowNodeData
import java.util.UUID

/**
 * Builds the single-node [WorkflowModel] DAG that api-server's generic Temporal schedule
 * executes for a KNIME workflow. The node's `nodeModel`/`propertiesSchema` mirror
 * `KNIMEWorkflowExecutorNodeModel` in the continuum-feature-knime repo — that contract is
 * duplicated here (not shared via a dependency) and must be kept in sync manually if the
 * executor node's properties ever change.
 */
object KnimeScheduleWorkflowMapper {

  const val KNIME_EXECUTOR_NODE_MODEL =
    "org.projectcontinuum.feature.knime.executor.node.KNIMEWorkflowExecutorNodeModel"

  private const val PROPERTY_WORKFLOW_LOCATION = "workflowLocation"
  private const val PROPERTY_TIMEOUT_SECONDS = "timeoutSeconds"
  private const val PROPERTY_RESET_WORKFLOW = "resetWorkflow"

  private val CONTENT_URL_PATTERN = Regex(""".*/api/v1/knime-workflows/([^/]+)/content$""")

  val KNIME_EXECUTOR_PROPERTIES_SCHEMA: Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
      PROPERTY_WORKFLOW_LOCATION to mapOf(
        "type" to "string",
        "title" to "Workflow Location",
        "description" to "Local path or URL to a KNIME .knwf workflow file: path/to/workflow.knwf, https://example.com/workflow.knwf, or s3://bucket/workflow.knwf"
      ),
      PROPERTY_TIMEOUT_SECONDS to mapOf(
        "type" to "integer",
        "title" to "Execution Timeout (seconds)",
        "default" to 300,
        "minimum" to 10
      ),
      PROPERTY_RESET_WORKFLOW to mapOf(
        "type" to "boolean",
        "title" to "Reset Workflow Before Execution",
        "description" to "Pass --reset to the KNIME batch executor to clear node outputs before running",
        "default" to false
      )
    ),
    "required" to listOf(PROPERTY_WORKFLOW_LOCATION)
  )

  fun toWorkflowModel(
    name: String,
    knimeWorkflowId: UUID,
    contentUrl: String,
    resetWorkflow: Boolean,
    timeoutSeconds: Long
  ): WorkflowModel {
    val node = WorkflowNode(
      id = knimeWorkflowId.toString(),
      type = "process",
      position = Position(0.0, 0.0),
      width = 100,
      height = 100,
      selected = false,
      data = WorkflowNodeData(
        title = "KNIME Workflow Executor",
        description = "Download, execute, and upload KNIME workflows",
        nodeModel = KNIME_EXECUTOR_NODE_MODEL,
        properties = mapOf(
          PROPERTY_WORKFLOW_LOCATION to contentUrl,
          PROPERTY_TIMEOUT_SECONDS to timeoutSeconds,
          PROPERTY_RESET_WORKFLOW to resetWorkflow
        ),
        propertiesSchema = KNIME_EXECUTOR_PROPERTIES_SCHEMA
      )
    )
    return WorkflowModel(id = knimeWorkflowId.toString(), name = name, nodes = listOf(node))
  }

  fun isKnimeExecutorWorkflow(model: WorkflowModel): Boolean =
    model.nodes.any { it.data.nodeModel == KNIME_EXECUTOR_NODE_MODEL }

  fun extractKnimeWorkflowId(model: WorkflowModel): UUID? {
    val workflowLocation = executorNode(model)?.data?.properties?.get(PROPERTY_WORKFLOW_LOCATION) as? String
      ?: return null
    val match = CONTENT_URL_PATTERN.matchEntire(workflowLocation) ?: return null
    return match.groupValues[1].let { runCatching { UUID.fromString(it) }.getOrNull() }
  }

  fun extractResetWorkflow(model: WorkflowModel): Boolean =
    executorNode(model)?.data?.properties?.get(PROPERTY_RESET_WORKFLOW) as? Boolean ?: false

  fun extractTimeoutSeconds(model: WorkflowModel): Long =
    (executorNode(model)?.data?.properties?.get(PROPERTY_TIMEOUT_SECONDS) as? Number)?.toLong() ?: 300L

  private fun executorNode(model: WorkflowModel): WorkflowNode? =
    model.nodes.firstOrNull { it.data.nodeModel == KNIME_EXECUTOR_NODE_MODEL }
}
