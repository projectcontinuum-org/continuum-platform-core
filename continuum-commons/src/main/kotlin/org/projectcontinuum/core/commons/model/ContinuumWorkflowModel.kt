package org.projectcontinuum.core.commons.model

import org.projectcontinuum.core.commons.protocol.progress.NodeProgress
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class ContinuumWorkflowModel @JsonCreator constructor(
  @param:JsonProperty("id") val id: String,
  @param:JsonProperty("name") val name: String,
  @param:JsonProperty("active") val active: Boolean = true,
  @param:JsonProperty("nodes") val nodes: List<Node> = emptyList(),
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

  data class Edge @JsonCreator constructor(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("type") val type: String? = null,
    @param:JsonProperty("animated") var animated: Boolean? = null,
    @param:JsonProperty("source") val source: String,
    @param:JsonProperty("target") val target: String,
    @param:JsonProperty("sourceHandle") val sourceHandle: String,
    @param:JsonProperty("targetHandle") val targetHandle: String
  )

  data class Node @JsonCreator constructor(
    @param:JsonProperty("id") val id: String,
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("position") val position: Position,
    @param:JsonProperty("data") val data: NodeData,
    @param:JsonProperty("width") val width: Int,
    @param:JsonProperty("height") val height: Int,
    @param:JsonProperty("selected") val selected: Boolean,
    @param:JsonProperty("positionAbsolute") val positionAbsolute: Position? = null,
    @param:JsonProperty("dragging") val dragging: Boolean? = null
  )

  data class Position @JsonCreator constructor(
    @param:JsonProperty("x") val x: Double,
    @param:JsonProperty("y") val y: Double
  )

  data class NodeData @JsonCreator constructor(
    @param:JsonProperty("id") val id: String? = null,
    @param:JsonProperty("description") val description: String,
    @param:JsonProperty("title") val title: String,
    @param:JsonProperty("subTitle") val subTitle: String? = null,
    @param:JsonProperty("icon") val icon: String? = null,
    @param:JsonProperty("nodeModel") val nodeModel: String,
    @param:JsonProperty("busy") val busy: Boolean? = null,
    @param:JsonProperty("inputs") val inputs: Map<String, NodePort>? = null,
    @param:JsonProperty("outputs") val outputs: Map<String, NodePort>? = null,
    @param:JsonProperty("properties") val properties: Map<String, Any> = mapOf(),
    @param:JsonProperty("propertiesSchema") val propertiesSchema: Map<String, Any> = mapOf(),
    @param:JsonProperty("propertiesUISchema") val propertiesUISchema: Map<String, Any> = mapOf(),
    @param:JsonProperty("status") var status: NodeStatus? = null,
    @param:JsonProperty("nodeProgress") var nodeProgress: NodeProgress? = null,
    @param:JsonProperty("retryOptions") val retryOptions: RetryOptionsConfig? = null
  )

  /**
   * Optional per-node override for Temporal activity retry behavior. Any field left null
   * falls back to the workflow-wide default. Field names/units mirror io.temporal.common.RetryOptions
   * to keep the mapping in the orchestration service trivial, without this model depending on the
   * Temporal SDK.
   */
  data class RetryOptionsConfig(
    @param:JsonProperty("initialIntervalSeconds") val initialIntervalSeconds: Long? = null,
    @param:JsonProperty("backoffCoefficient") val backoffCoefficient: Double? = null,
    @param:JsonProperty("maximumIntervalSeconds") val maximumIntervalSeconds: Long? = null,
    @param:JsonProperty("maximumAttempts") val maximumAttempts: Int? = null,
    @param:JsonProperty("doNotRetry") val doNotRetry: List<String>? = null
  )

  data class NodePort @JsonCreator constructor(
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("contentType") val contentType: String
  )

  enum class NodeStatus {
    ACTIVE,
    CONFIGURED,
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