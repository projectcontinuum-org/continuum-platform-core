package org.projectcontinuum.core.knime.scheduler.util

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import java.util.UUID

/**
 * Builds the single-node [ContinuumWorkflowModel] DAG that api-server's generic Temporal schedule
 * executes for a KNIME workflow. The node's `nodeModel`/`propertiesSchema` mirror
 * `KNIMEWorkflowExecutorNodeModel` in the continuum-feature-knime repo — that contract is
 * duplicated here (not shared via a dependency) and must be kept in sync manually if the
 * executor node's properties ever change. `NodeData.id` must equal `NodeData.nodeModel`:
 * `WorkflowActivityInitializer` in continuum-orchestration-service uses `data.id` as the node-type
 * key to resolve task queues from api-server's node registry, which is keyed by `nodeModel`.
 */
object KnimeScheduleWorkflowMapper {

  const val KNIME_EXECUTOR_NODE_MODEL =
    "org.projectcontinuum.feature.knime.executor.node.KNIMEWorkflowExecutorNodeModel"

  private const val PROPERTY_WORKFLOW_LOCATION = "workflowLocation"
  private const val PROPERTY_EXECUTION_UPLOAD_URL = "executionUploadUrl"
  private const val PROPERTY_TIMEOUT_SECONDS = "timeoutSeconds"
  private const val PROPERTY_RESET_WORKFLOW = "resetWorkflow"

  // Trailing /{fileName} segment is optional so schedules created before that segment was
  // added (URL ending bare in /content) still resolve.
  private val CONTENT_URL_PATTERN = Regex(""".*/api/v1/knime-workflows/([^/]+)/content(?:/[^/]+)?$""")

  val KNIME_EXECUTOR_PROPERTIES_SCHEMA: Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
      PROPERTY_WORKFLOW_LOCATION to mapOf(
        "type" to "string",
        "title" to "Workflow Location",
        "description" to "Local path or URL to a KNIME workflow: path/to/workflow.knwf, s3://bucket/workflow.knwf, or any http(s):// URL that returns a valid KNIME workflow archive (the .knwf extension is not required for http(s) URLs)"
      ),
      PROPERTY_EXECUTION_UPLOAD_URL to mapOf(
        "type" to "string",
        "title" to "Execution Result Upload URL",
        "description" to "HTTP(S) URL the executed .knwf is POSTed back to (multipart/form-data) once the workflow finishes, success or failure"
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
    "required" to listOf(PROPERTY_WORKFLOW_LOCATION, PROPERTY_EXECUTION_UPLOAD_URL)
  )

  fun toWorkflowModel(
    name: String,
    knimeWorkflowId: UUID,
    contentUrl: String,
    executionUploadUrl: String,
    resetWorkflow: Boolean,
    timeoutSeconds: Long
  ): ContinuumWorkflowModel {
    val node = ContinuumWorkflowModel.Node(
      id = knimeWorkflowId.toString(),
      type = "process",
      position = ContinuumWorkflowModel.Position(0.0, 0.0),
      width = 100,
      height = 100,
      selected = false,
      data = ContinuumWorkflowModel.NodeData(
        id = KNIME_EXECUTOR_NODE_MODEL,
        title = "KNIME Workflow Executor",
        description = "Download, execute, and upload KNIME workflows",
        nodeModel = KNIME_EXECUTOR_NODE_MODEL,
        properties = mapOf(
          PROPERTY_WORKFLOW_LOCATION to contentUrl,
          PROPERTY_EXECUTION_UPLOAD_URL to executionUploadUrl,
          PROPERTY_TIMEOUT_SECONDS to timeoutSeconds,
          PROPERTY_RESET_WORKFLOW to resetWorkflow
        ),
        propertiesSchema = KNIME_EXECUTOR_PROPERTIES_SCHEMA
      )
    )
    return ContinuumWorkflowModel(id = knimeWorkflowId.toString(), name = name, nodes = listOf(node))
  }

  fun isKnimeExecutorWorkflow(model: ContinuumWorkflowModel): Boolean =
    model.nodes.any { it.data.nodeModel == KNIME_EXECUTOR_NODE_MODEL }

  fun extractKnimeWorkflowId(model: ContinuumWorkflowModel): UUID? {
    val workflowLocation = executorNode(model)?.data?.properties?.get(PROPERTY_WORKFLOW_LOCATION) as? String
      ?: return null
    val match = CONTENT_URL_PATTERN.matchEntire(workflowLocation) ?: return null
    return match.groupValues[1].let { runCatching { UUID.fromString(it) }.getOrNull() }
  }

  fun extractResetWorkflow(model: ContinuumWorkflowModel): Boolean =
    executorNode(model)?.data?.properties?.get(PROPERTY_RESET_WORKFLOW) as? Boolean ?: false

  fun extractTimeoutSeconds(model: ContinuumWorkflowModel): Long =
    (executorNode(model)?.data?.properties?.get(PROPERTY_TIMEOUT_SECONDS) as? Number)?.toLong() ?: 300L

  private fun executorNode(model: ContinuumWorkflowModel): ContinuumWorkflowModel.Node? =
    model.nodes.firstOrNull { it.data.nodeModel == KNIME_EXECUTOR_NODE_MODEL }
}
