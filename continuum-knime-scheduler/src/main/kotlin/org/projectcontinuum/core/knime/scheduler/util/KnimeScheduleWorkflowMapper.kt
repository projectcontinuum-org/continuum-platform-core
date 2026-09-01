package org.projectcontinuum.core.knime.scheduler.util

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.knime.scheduler.model.KnimeWorkflowCredentialRef
import org.projectcontinuum.core.knime.scheduler.model.KnimeWorkflowVariable
import java.util.UUID

/**
 * Builds the single-node [ContinuumWorkflowModel] DAG that api-server's generic Temporal schedule
 * executes for a KNIME workflow. The node's `nodeModel`/`propertiesSchema`/`icon`/`subTitle`/
 * `inputs`/`outputs`/`propertiesUISchema` mirror `KNIMEWorkflowExecutorNodeModel.metadata` in the
 * continuum-feature-knime repo — that contract is duplicated here (not shared via a dependency)
 * and must be kept in sync manually if the executor node's properties ever change. `Node.type`
 * must be `"BaseNode"`: that's the only node type the workflow-editor's React Flow instance has
 * registered, so anything else silently falls back to React Flow's unstyled default node.
 * `NodeData.id` must equal `NodeData.nodeModel`: `WorkflowActivityInitializer` in
 * continuum-orchestration-service uses `data.id` as the node-type key to resolve task queues from
 * api-server's node registry, which is keyed by `nodeModel`.
 */
object KnimeScheduleWorkflowMapper {

  const val KNIME_EXECUTOR_NODE_MODEL =
    "org.projectcontinuum.feature.knime.executor.node.KNIMEWorkflowExecutorNodeModel"

  private const val PROPERTY_WORKFLOW_LOCATION = "workflowLocation"
  private const val PROPERTY_EXECUTION_UPLOAD_URL = "executionUploadUrl"
  private const val PROPERTY_TIMEOUT_SECONDS = "timeoutSeconds"
  private const val PROPERTY_RESET_WORKFLOW = "resetWorkflow"
  private const val PROPERTY_WORKFLOW_VARIABLES = "workflowVariables"
  private const val PROPERTY_WORKFLOW_CREDENTIALS = "workflowCredentials"

  private const val NODE_SUB_TITLE = "Execute KNIME workflows from remote locations"

  private val NODE_ICON = """
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
      <path d="m10.445 21.393 11.54 -0.775 0.451 0.775zM7.56 11.113l-5.092 10.28h-0.904Zm10.427 2.652 -6.43 -9.505 0.452 -0.775zm2.57 5.216 0.627 0.896 -10.652 0.707zM4.655 20.976l-1.143 0.09 4.709 -9.488Zm6.173 -14.667 0.476 -0.998 5.984 8.782zM19.1 17.364l0.847 1.015 -8.685 1.413zM6.76 20.532l-1.32 0.224 3.11 -8.162Zm3.406 -12.189 0.472 -1.207 5.558 6.732Zm7.403 7.54 1.13 1.016 -6.378 1.98zm-8.759 4.08 -1.46 0.448 1.46 -6.44zm0.8 -9.539 0.363 -1.48 4.868 4.477zm-0.348 9.402v-7.851l0.244 -1.085 6.864 3.926 0.834 0.758L10.34 19.5zM12.01 1.694 0 22.306h24z" fill="#000000" stroke-width="1"></path>
    </svg>
  """.trimIndent()

  private val NODE_OUTPUTS: Map<String, ContinuumWorkflowModel.NodePort> = mapOf(
    "destinationPath" to ContinuumWorkflowModel.NodePort(name = "destination path", contentType = "text/plain")
  )

  private val NODE_INPUTS: Map<String, ContinuumWorkflowModel.NodePort> = emptyMap()

  val KNIME_EXECUTOR_PROPERTIES_UI_SCHEMA: Map<String, Any> = mapOf(
    "type" to "Categorization",
    "elements" to listOf(
      mapOf(
        "type" to "Category",
        "label" to "Workflow Configuration",
        "elements" to listOf(
          mapOf(
            "type" to "Control",
            "scope" to "#/properties/$PROPERTY_WORKFLOW_LOCATION",
            "options" to mapOf(
              "placeholder" to "https://example.com/workflow.knwf or /path/to/workflow.knwf"
            )
          ),
          mapOf(
            "type" to "Control",
            "scope" to "#/properties/$PROPERTY_EXECUTION_UPLOAD_URL",
            "options" to mapOf(
              "placeholder" to "https://example.com/api/v1/knime-workflows/{workflowId}/executions"
            )
          )
        )
      ),
      mapOf(
        "type" to "Category",
        "label" to "Execution Settings",
        "elements" to listOf(
          mapOf("type" to "Control", "scope" to "#/properties/$PROPERTY_TIMEOUT_SECONDS"),
          mapOf("type" to "Control", "scope" to "#/properties/$PROPERTY_RESET_WORKFLOW")
        )
      ),
      mapOf(
        "type" to "Category",
        "label" to "Workflow Variables & Credentials",
        "elements" to listOf(
          mapOf(
            "type" to "Control",
            "scope" to "#/properties/$PROPERTY_WORKFLOW_VARIABLES",
            "options" to mapOf("showSortButtons" to true)
          ),
          mapOf(
            "type" to "Control",
            "scope" to "#/properties/$PROPERTY_WORKFLOW_CREDENTIALS",
            "options" to mapOf(
              "showSortButtons" to true,
              "detail" to mapOf(
                "type" to "VerticalLayout",
                "elements" to listOf(
                  mapOf("type" to "Control", "scope" to "#/properties/knimeCredentialName"),
                  mapOf(
                    "type" to "Control",
                    "scope" to "#/properties/credential",
                    "options" to mapOf("format" to "credential", "credentialType" to "GENERIC")
                  )
                )
              )
            )
          )
        )
      )
    )
  )

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
      ),
      PROPERTY_WORKFLOW_VARIABLES to mapOf(
        "type" to "array",
        "title" to "Workflow Variables",
        "description" to "Variables passed to the workflow at runtime via NodePit's --variable flag. Configure matching Workflow Variables in KNIME first (right-click workflow -> Workflow Variables...)",
        "items" to mapOf(
          "type" to "object",
          "properties" to mapOf(
            "name" to mapOf("type" to "string", "title" to "Name"),
            "value" to mapOf("type" to "string", "title" to "Value"),
            "type" to mapOf(
              "type" to "string",
              "title" to "Type",
              "enum" to listOf("String", "int", "double"),
              "default" to "String"
            )
          ),
          "required" to listOf("name", "value", "type")
        ),
        "default" to emptyList<Any>()
      ),
      PROPERTY_WORKFLOW_CREDENTIALS to mapOf(
        "type" to "array",
        "title" to "Workflow Credentials",
        "description" to "Login/password credentials injected into the workflow at runtime via NodePit's --credential flag. Configure a matching entry in KNIME first (right-click workflow -> Workflow Credentials...) - the Name below must exactly match the name configured there.",
        "items" to mapOf(
          "type" to "object",
          "properties" to mapOf(
            "knimeCredentialName" to mapOf(
              "type" to "string",
              "title" to "KNIME Credential Name",
              "description" to "Must exactly match the name configured in this workflow's own Workflow Credentials dialog in KNIME"
            ),
            "credential" to mapOf(
              "type" to "string",
              "title" to "Credential",
              "description" to "Continuum stored credential providing the login and password to inject"
            )
          ),
          "required" to listOf("knimeCredentialName", "credential")
        ),
        "default" to emptyList<Any>()
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
    timeoutSeconds: Long,
    workflowVariables: List<KnimeWorkflowVariable> = emptyList(),
    workflowCredentials: List<KnimeWorkflowCredentialRef> = emptyList()
  ): ContinuumWorkflowModel {
    val position = ContinuumWorkflowModel.Position(0.0, 0.0)
    val node = ContinuumWorkflowModel.Node(
      id = knimeWorkflowId.toString(),
      type = "BaseNode",
      position = position,
      positionAbsolute = position,
      width = 255,
      height = 123,
      selected = false,
      dragging = false,
      data = ContinuumWorkflowModel.NodeData(
        id = KNIME_EXECUTOR_NODE_MODEL,
        title = "KNIME Workflow Executor",
        subTitle = NODE_SUB_TITLE,
        description = "Download, execute, and upload KNIME workflows",
        nodeModel = KNIME_EXECUTOR_NODE_MODEL,
        icon = NODE_ICON,
        inputs = NODE_INPUTS,
        outputs = NODE_OUTPUTS,
        properties = mapOf(
          PROPERTY_WORKFLOW_LOCATION to contentUrl,
          PROPERTY_EXECUTION_UPLOAD_URL to executionUploadUrl,
          PROPERTY_TIMEOUT_SECONDS to timeoutSeconds,
          PROPERTY_RESET_WORKFLOW to resetWorkflow,
          PROPERTY_WORKFLOW_VARIABLES to workflowVariables.map {
            mapOf("name" to it.name, "value" to it.value, "type" to it.type)
          },
          PROPERTY_WORKFLOW_CREDENTIALS to workflowCredentials.map {
            mapOf("knimeCredentialName" to it.knimeCredentialName, "credential" to it.credential)
          }
        ),
        propertiesSchema = KNIME_EXECUTOR_PROPERTIES_SCHEMA,
        propertiesUISchema = KNIME_EXECUTOR_PROPERTIES_UI_SCHEMA
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

  fun extractWorkflowVariables(model: ContinuumWorkflowModel): List<KnimeWorkflowVariable> =
    (executorNode(model)?.data?.properties?.get(PROPERTY_WORKFLOW_VARIABLES) as? List<*>)
      ?.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
          val name = m["name"] as? String ?: return@let null
          KnimeWorkflowVariable(
            name = name,
            value = m["value"] as? String ?: "",
            type = m["type"] as? String ?: "String"
          )
        }
      } ?: emptyList()

  fun extractWorkflowCredentials(model: ContinuumWorkflowModel): List<KnimeWorkflowCredentialRef> =
    (executorNode(model)?.data?.properties?.get(PROPERTY_WORKFLOW_CREDENTIALS) as? List<*>)
      ?.mapNotNull { item ->
        (item as? Map<*, *>)?.let { m ->
          val knimeCredentialName = m["knimeCredentialName"] as? String ?: return@let null
          val credential = m["credential"] as? String ?: return@let null
          KnimeWorkflowCredentialRef(knimeCredentialName = knimeCredentialName, credential = credential)
        }
      } ?: emptyList()

  private fun executorNode(model: ContinuumWorkflowModel): ContinuumWorkflowModel.Node? =
    model.nodes.firstOrNull { it.data.nodeModel == KNIME_EXECUTOR_NODE_MODEL }
}
