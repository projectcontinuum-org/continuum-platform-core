package org.projectcontinuum.core.commons.model

import org.projectcontinuum.core.commons.protocol.progress.NodeProgress
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
  name = "ContinuumWorkflowModel",
  description = "A Continuum workflow: a directed acyclic graph of nodes and edges, the content of a `.cwf` file in the " +
    "workbench (React Flow format)."
)
data class ContinuumWorkflowModel @JsonCreator constructor(
  @field:Schema(description = "Workflow id, chosen by the workbench (64 hex characters for generated workflows).", example = "a3f1c9e2b7d84f60a1c2e3d4f5a6b7c8d9e0f1a2b3c4d5e6f708192a3b4c5d6e")
  @param:JsonProperty("id") val id: String,
  @field:Schema(
    description = "Workflow name, normally the `.cwf` path in the workbench workspace. A run stores it as its `workflowUri` " +
      "and the run history groups by it, so it must be a valid relative URI (no spaces).",
    example = "examples/text-cleanup.cwf"
  )
  @param:JsonProperty("name") val name: String,
  @field:Schema(description = "Whether the workflow is active in the editor. Not used by the backend.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "true", example = "true")
  @param:JsonProperty("active") val active: Boolean = true,
  @field:Schema(description = "The nodes of the graph.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @param:JsonProperty("nodes") val nodes: List<Node> = emptyList(),
  @field:Schema(description = "The edges of the graph: each connects one output port to one input port.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @param:JsonProperty("edges") val edges: List<Edge> = emptyList()
) {
  @JsonIgnore
  private val nodesMap: Map<String, Node> = nodes.associateBy { it.id }

  @JsonIgnore
  private val nodeParents: Map<String, List<String>> =
    edges.groupBy { it.target }.mapValues { it.value.map { edge -> edge.source } }

  @JsonIgnore
  private val nodeParentEdges: Map<String, List<Edge>> = edges.groupBy { it.target }

  @JsonIgnore
  fun getRootNodes(): List<Node> {
    return nodes.filter { node -> !nodeParents.containsKey(node.id) }
  }

  @JsonIgnore
  fun getParentNodes(node: Node): List<Node> {
    return nodeParents[node.id]?.mapNotNull { nodesMap[it] } ?: emptyList()
  }

  @JsonIgnore
  fun getParentEdges(node: Node): List<Edge> {
    return nodeParentEdges[node.id] ?: emptyList()
  }

  @Schema(name = "WorkflowEdge", description = "An edge from an output port of one node to an input port of another.")
  data class Edge @JsonCreator constructor(
    @field:Schema(description = "Edge id, unique in the workflow.", example = "reactflow__edge-createdata-normalizedata")
    @param:JsonProperty("id") val id: String,
    @field:Schema(description = "React Flow edge type. The workbench uses `BaseEdge`.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "BaseEdge")
    @param:JsonProperty("type") val type: String? = null,
    @field:Schema(description = "Whether the editor animates the edge. Set by the orchestrator while data flows.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "false")
    @param:JsonProperty("animated") var animated: Boolean? = null,
    @field:Schema(description = "Id of the source node (`nodes[].id`).", example = "create")
    @param:JsonProperty("source") val source: String,
    @field:Schema(description = "Id of the target node (`nodes[].id`).", example = "normalize")
    @param:JsonProperty("target") val target: String,
    @field:Schema(description = "Output port id of the source node (a key of its `data.outputs`).", example = "data")
    @param:JsonProperty("sourceHandle") val sourceHandle: String,
    @field:Schema(description = "Input port id of the target node (a key of its `data.inputs`). One edge per input port.", example = "data")
    @param:JsonProperty("targetHandle") val targetHandle: String,
    @field:Schema(description = "Runtime state set by the orchestrator. Absent until the run reaches this edge.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @param:JsonProperty("data") var data: EdgeData? = null
  )

  /** Runtime state of an edge, set by the orchestrator. Under `data` so React Flow passes it to the edge component. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Schema(name = "WorkflowEdgeData", description = "Runtime state of an edge, set by the orchestrator.")
  data class EdgeData(
    @field:Schema(description = "Edge status. Absent (null) while the source node has not finished.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "DELIVERED")
    @param:JsonProperty("status") val status: EdgeStatus? = null
  )

  /** Runtime state of an edge. `null` means its source has not finished yet. Mirrored by `EdgeStatus` in the workbench. */
  @Schema(
    name = "EdgeStatus",
    description = "`DELIVERED`: the source finished and produced the edge's source port. `BROKEN`: the edge will never " +
      "carry data (its source failed or was skipped, or finished without producing the port)."
  )
  enum class EdgeStatus {
    /** The source finished and produced the edge's source port. */
    DELIVERED,

    /** The edge will never carry data: its source failed or was skipped, or finished without producing the port. */
    BROKEN
  }

  @Schema(name = "WorkflowNode", description = "A node of the workflow: an instance of a registered node type with its properties and editor layout.")
  data class Node @JsonCreator constructor(
    @field:Schema(description = "Node id, unique in the workflow. Edges and node outputs refer to it.", example = "normalize")
    @param:JsonProperty("id") val id: String,
    @field:Schema(description = "React Flow node type. The workbench uses `BaseNode`.", example = "BaseNode")
    @param:JsonProperty("type") val type: String,
    @field:Schema(description = "Position of the node in the editor canvas.")
    @param:JsonProperty("position") val position: Position,
    @field:Schema(description = "The node type, its properties and its runtime state.")
    @param:JsonProperty("data") val data: NodeData,
    @field:Schema(description = "Rendered width in the editor, in pixels.", example = "250")
    @param:JsonProperty("width") val width: Int,
    @field:Schema(description = "Rendered height in the editor, in pixels.", example = "120")
    @param:JsonProperty("height") val height: Int,
    @field:Schema(description = "Whether the node is selected in the editor. Not used by the backend.", example = "false")
    @param:JsonProperty("selected") val selected: Boolean,
    @field:Schema(description = "Absolute canvas position (React Flow). Not used by the backend.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @param:JsonProperty("positionAbsolute") val positionAbsolute: Position? = null,
    @field:Schema(description = "Whether the node is being dragged in the editor. Not used by the backend.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "false")
    @param:JsonProperty("dragging") val dragging: Boolean? = null
  )

  @Schema(name = "CanvasPosition", description = "A point on the editor canvas, in pixels.")
  data class Position @JsonCreator constructor(
    @field:Schema(description = "Horizontal position in pixels.", example = "330.0")
    @param:JsonProperty("x") val x: Double,
    @field:Schema(description = "Vertical position in pixels.", example = "0.0")
    @param:JsonProperty("y") val y: Double
  )

  @Schema(
    name = "NodeData",
    description = "A node type's definition (as registered by its feature worker) plus, inside a workflow, the instance's " +
      "properties and runtime state. When a workflow is started or validated, `inputs`, `outputs` and `propertiesSchema` " +
      "come from the node registry; the copies in the request are only compared (a difference is a warning)."
  )
  data class NodeData @JsonCreator constructor(
    @field:Schema(description = "Node type id; the same as `nodeModel` for registered nodes.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel")
    @param:JsonProperty("id") val id: String? = null,
    @field:Schema(description = "What the node does, shown in the palette.", example = "Normalizes text by trimming, lowercasing, and removing non-alphanumeric characters")
    @param:JsonProperty("description") val description: String,
    @field:Schema(description = "Display name of the node type.", example = "Text Normalizer")
    @param:JsonProperty("title") val title: String,
    @field:Schema(description = "Short secondary label.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "Clean and normalize text")
    @param:JsonProperty("subTitle") val subTitle: String? = null,
    @field:Schema(description = "Inline SVG icon markup.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\"></svg>")
    @param:JsonProperty("icon") val icon: String? = null,
    @field:Schema(
      description = "Registered node type: the fully qualified class name of the node model in its feature worker. " +
        "Decides which worker (task queue) runs the node.",
      example = "org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel"
    )
    @param:JsonProperty("nodeModel") val nodeModel: String,
    @field:Schema(description = "Editor busy flag. Not used by the backend.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "false")
    @param:JsonProperty("busy") val busy: Boolean? = null,
    @field:Schema(description = "Input ports: port id → port. Absent for source (trigger) nodes.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @param:JsonProperty("inputs") val inputs: Map<String, NodePort>? = null,
    @field:Schema(description = "Output ports: port id → port.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @param:JsonProperty("outputs") val outputs: Map<String, NodePort>? = null,
    @field:Schema(
      description = "Property values of this node, validated against the registered `propertiesSchema` (JSON Schema draft 7) " +
        "when the workflow is started, scheduled or validated. In the registry: the defaults.",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED,
      example = "{\"inputCol\": \"name\", \"outputCol\": \"clean_name\"}"
    )
    @param:JsonProperty("properties") val properties: Map<String, Any> = mapOf(),
    @field:Schema(description = "JSON Schema (draft 7) of `properties`. The registered one is authoritative.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @param:JsonProperty("propertiesSchema") val propertiesSchema: Map<String, Any> = mapOf(),
    @field:Schema(description = "JSONForms UI schema the workbench renders the property dialog from. Not used by the backend.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @param:JsonProperty("propertiesUISchema") val propertiesUISchema: Map<String, Any> = mapOf(),
    @field:Schema(description = "Runtime status of the node, set by the orchestrator during a run. Null before the run reaches it.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "SUCCESS")
    @param:JsonProperty("status") var status: NodeStatus? = null,
    @field:Schema(description = "Latest progress reported by the worker during a run.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @param:JsonProperty("nodeProgress") var nodeProgress: NodeProgress? = null,
    @field:Schema(description = "Per-node override of the Temporal activity retry policy.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @param:JsonProperty("retryOptions") val retryOptions: RetryOptionsConfig? = null
  )

  /**
   * Optional per-node override for Temporal activity retry behavior. Any field left null
   * falls back to the workflow-wide default. Field names/units mirror io.temporal.common.RetryOptions
   * to keep the mapping in the orchestration service trivial, without this model depending on the
   * Temporal SDK.
   */
  @Schema(name = "NodeRetryOptions", description = "Per-node Temporal activity retry policy. A null field falls back to the workflow-wide default.")
  data class RetryOptionsConfig(
    @field:Schema(description = "Delay before the first retry, in seconds.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "1")
    @param:JsonProperty("initialIntervalSeconds") val initialIntervalSeconds: Long? = null,
    @field:Schema(description = "Factor the delay grows by after each retry.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "2.0")
    @param:JsonProperty("backoffCoefficient") val backoffCoefficient: Double? = null,
    @field:Schema(description = "Upper bound of the delay between retries, in seconds.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "60")
    @param:JsonProperty("maximumIntervalSeconds") val maximumIntervalSeconds: Long? = null,
    @field:Schema(description = "Maximum number of attempts including the first; 1 disables retries.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "3")
    @param:JsonProperty("maximumAttempts") val maximumAttempts: Int? = null,
    @field:Schema(description = "Exception class names that are never retried.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "[\"java.lang.IllegalArgumentException\"]")
    @param:JsonProperty("doNotRetry") val doNotRetry: List<String>? = null
  )

  @Schema(name = "NodePort", description = "An input or output port of a node type.")
  data class NodePort @JsonCreator constructor(
    @field:Schema(description = "Display name of the port.", example = "input table")
    @param:JsonProperty("name") val name: String,
    @field:Schema(description = "Media type of the data on the port; tables are Parquet.", example = "text/plain")
    @param:JsonProperty("contentType") val contentType: String
  )

  /** Mirrored by `NodeStatus` in the workbench (`continuum-core/src/types/IBaseNode.ts`); keep both in sync. */
  @Schema(
    name = "NodeStatus",
    description = "Runtime status of a node. `SCHEDULED`: dispatched to its worker. `BUSY`: the worker started it. " +
      "`PRE_PROCESSING` / `POST_PROCESSING`: downloading inputs / uploading outputs. `SUCCESS`, `FAILED`, `CANCELLED`: " +
      "finished. `WARNING`: finished with warnings. `SKIPPED`: can never run (an input will never get data). " +
      "`ACTIVE` / `CONFIGURED`: editor states."
  )
  enum class NodeStatus {
    ACTIVE,
    CONFIGURED,
    SCHEDULED,
    BUSY,
    SUCCESS,
    FAILED,
    CANCELLED,
    WARNING,
    PRE_PROCESSING,
    POST_PROCESSING,
    SKIPPED
  }
}