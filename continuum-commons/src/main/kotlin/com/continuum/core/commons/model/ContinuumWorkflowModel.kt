package com.continuum.core.commons.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class ContinuumWorkflowModel @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("active") val active: Boolean = true,
    @JsonProperty("nodes") val nodes: List<Node> = emptyList(),
    @JsonProperty("edges") val edges: List<Edge> = emptyList()
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
        @JsonProperty("id") val id: String,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("animated") var animated: Boolean? = null,
        @JsonProperty("source") val source: String,
        @JsonProperty("target") val target: String,
        @JsonProperty("sourceHandle") val sourceHandle: String,
        @JsonProperty("targetHandle") val targetHandle: String
    )

    data class Node @JsonCreator constructor(
        @JsonProperty("id") val id: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("position") val position: Position,
        @JsonProperty("data") val data: NodeData,
        @JsonProperty("width") val width: Int,
        @JsonProperty("height") val height: Int,
        @JsonProperty("selected") val selected: Boolean,
        @JsonProperty("positionAbsolute") val positionAbsolute: Position? = null,
        @JsonProperty("dragging") val dragging: Boolean
    )

    data class Position @JsonCreator constructor(
        @JsonProperty("x") val x: Double,
        @JsonProperty("y") val y: Double
    )

    data class NodeData @JsonCreator constructor(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("description") val description: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("subTitle") val subTitle: String? = null,
        @JsonProperty("icon") val icon: String? = null,
        @JsonProperty("nodeModel") val nodeModel: String,
        @JsonProperty("busy") val busy: Boolean? = null,
        @JsonProperty("inputs") val inputs: Map<String, NodePort>? = null,
        @JsonProperty("outputs") val outputs: Map<String, NodePort>? = null,
        @JsonProperty("properties") val properties: Map<String, Any> = mapOf(),
        @JsonProperty("propertiesSchema") val propertiesSchema: Map<String, Any> = mapOf(),
        @JsonProperty("propertiesUISchema") val propertiesUISchema: Map<String, Any> = mapOf(),
        @JsonProperty("status") var status: NodeStatus? = null
    )

    data class NodePort @JsonCreator constructor(
        @JsonProperty("name") val name: String,
        @JsonProperty("contentType") val contentType: String
    )

    enum class NodeStatus {
        ACTIVE,
        CONFIGURED,
        BUSY,
        SUCCESS,
        FAILED,
        WARNING,
        PRE_PROCESSING,
        POST_PROCESSING
    }
}