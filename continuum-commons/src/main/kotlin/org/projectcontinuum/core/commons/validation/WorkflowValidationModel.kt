package org.projectcontinuum.core.commons.validation

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

/** Where a node's registered definition (ports, properties schema, defaults) comes from. Implemented by the API server over the node registry. */
fun interface NodeDescriptorSource {
  /** The registered definition of [nodeModel], or null when no such node is registered. */
  fun find(nodeModel: String): ContinuumWorkflowModel.NodeData?
}

/**
 * How strict the property checks are, and how messages are worded.
 *
 * - [AUTHORING]: a workflow being built from a partial spec. Given properties are merged over the registered
 *   defaults, keys the schema does not declare are errors, and an empty required value counts as missing.
 * - [RUN]: a complete workflow about to be started. Properties are checked exactly as sent, the way the worker will
 *   check them, using the schema alone. Nothing the workbench or an old workflow stored is rejected for being extra
 *   unless the schema itself forbids it.
 */
enum class ValidationMode { AUTHORING, RUN }

/**
 * A node to check. [key] identifies it in edges, [nodeId] is the registered node id, [embedded] is the definition the
 * client sent (RUN only). [type] is the React Flow node type when the caller has one (`BaseNode`, `Loop`); [parent] is
 * the key of the loop the node is directly inside (`Node.parentNode`), null at the top level.
 */
data class GraphNode(
  val key: String?,
  val nodeId: String?,
  val properties: Map<String, Any?>? = null,
  val embedded: ContinuumWorkflowModel.NodeData? = null,
  val type: String? = null,
  val parent: String? = null
)

data class GraphEdge(val from: String?, val fromPort: String?, val to: String?, val toPort: String?)

/** One problem found by [WorkflowValidator]. node, edge and property say where it is, when that applies. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "WorkflowIssue", description = "One problem found by the workflow validator. `node`, `edge` and `property` say where it is, when that applies; absent ones are omitted.")
data class WorkflowIssue(
  @field:Schema(description = "Human-readable description of the issue.", example = "Node 'normalize': property 'inputCol' is required.")
  val message: String,
  @field:Schema(description = "Id of the node concerned (`nodes[].id`).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "normalize")
  val node: String? = null,
  @field:Schema(description = "Id of the edge concerned (`edges[].id`).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "reactflow__edge-createdata-normalizedata")
  val edge: String? = null,
  @field:Schema(description = "Top-level node property concerned.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "inputCol")
  val property: String? = null
)

data class WorkflowValidationResult(val errors: List<WorkflowIssue>, val warnings: List<WorkflowIssue>) {
  val valid: Boolean get() = errors.isEmpty()
}
