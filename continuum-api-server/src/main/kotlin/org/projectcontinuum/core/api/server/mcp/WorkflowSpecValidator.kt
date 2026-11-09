package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.ValidationHelper
import org.springframework.stereotype.Component

/**
 * Checks a compact workflow spec against the live node registry: node ids, keys, ports, edges, cycles and each node's
 * properties (merged over the registry defaults) against its JSON Schema. Every problem is collected, not just the first,
 * so a caller can fix them all in one pass.
 */
@Component
class WorkflowSpecValidator(
    private val nodeExplorerService: NodeExplorerService
) {

    private class ResolvedNode(val key: String, val nodeId: String, val metadata: ContinuumWorkflowModel.NodeData?)

    fun validate(name: String?, nodes: List<McpWorkflowSpecNode>?, edges: List<McpWorkflowSpecEdge>?): McpWorkflowValidationResult {
        val errors = mutableListOf<McpWorkflowIssue>()
        val warnings = mutableListOf<McpWorkflowIssue>()
        val specNodes = nodes.orEmpty()
        val specEdges = edges.orEmpty()

        if (name.isNullOrBlank()) errors += McpWorkflowIssue("The workflow needs a non-empty name.")
        // Allowed so a client can save an empty workflow first and fill it in node by node
        if (specNodes.isEmpty()) warnings += McpWorkflowIssue("The workflow has no nodes yet.")

        val byKey = validateNodes(specNodes, errors)
        val validEdges = validateEdges(specEdges, byKey, errors)

        val cycle = findCycle(byKey.keys, validEdges)
        if (cycle.isNotEmpty()) {
            errors += McpWorkflowIssue(
                "The edges contain a cycle through nodes ${cycle.joinToString(", ") { "'$it'" }}. A workflow must be a directed acyclic graph."
            )
        }

        collectConnectionWarnings(byKey, validEdges, warnings)

        return McpWorkflowValidationResult(
            valid = errors.isEmpty(),
            nodeCount = specNodes.size,
            edgeCount = specEdges.size,
            errors = errors,
            warnings = warnings
        )
    }

    private fun validateNodes(nodes: List<McpWorkflowSpecNode>, errors: MutableList<McpWorkflowIssue>): Map<String, ResolvedNode> {
        val byKey = linkedMapOf<String, ResolvedNode>()
        val registry = mutableMapOf<String, ContinuumWorkflowModel.NodeData?>()

        nodes.forEachIndexed { index, node ->
            val key = node.key
            if (key.isNullOrBlank()) {
                errors += McpWorkflowIssue("Node #${index + 1} needs a non-empty key.")
                return@forEachIndexed
            }
            if (key in byKey) {
                errors += McpWorkflowIssue("Node key '$key' is used more than once. Every node needs a unique key.", node = key)
                return@forEachIndexed
            }
            val nodeId = node.nodeId
            if (nodeId.isNullOrBlank()) {
                errors += McpWorkflowIssue("Node '$key' needs a nodeId.", node = key)
                byKey[key] = ResolvedNode(key, "", null)
                return@forEachIndexed
            }
            if (nodeId !in registry) registry[nodeId] = nodeExplorerService.getNodeMetadata(nodeId)
            val metadata = registry[nodeId]
            byKey[key] = ResolvedNode(key, nodeId, metadata)
            if (metadata == null) {
                errors += McpWorkflowIssue(
                    "Node '$key': no registered node with id '$nodeId'. Use the full node id exactly as returned by " +
                        "searchContinuumNodes or listContinuumNodeCategories.",
                    node = key
                )
                return@forEachIndexed
            }
            validateProperties(key, node.properties.orEmpty(), metadata, errors)
        }
        return byKey
    }

    private fun validateProperties(
        key: String,
        given: Map<String, Any?>,
        metadata: ContinuumWorkflowModel.NodeData,
        errors: MutableList<McpWorkflowIssue>
    ) {
        val schema = metadata.propertiesSchema
        val declared = (schema["properties"] as? Map<*, *>)?.keys?.map { it.toString() }.orEmpty()

        for (property in given.keys - declared.toSet()) {
            errors += McpWorkflowIssue(
                "Node '$key': unknown property '$property'. Known properties: ${declared.joinToString(", ").ifEmpty { "(none)" }}.",
                node = key,
                property = property
            )
        }

        val merged: Map<String, Any?> = metadata.properties + given
        val required = (schema["required"] as? List<*>)?.map { it.toString() }.orEmpty()
        for (property in required) {
            val value = merged[property]
            if (value == null || value == "") {
                errors += McpWorkflowIssue(
                    "Node '$key': required property '$property' has no value. Set it in properties.",
                    node = key,
                    property = property
                )
            }
        }

        if (schema.isEmpty()) return
        // Unknown keys and missing required values are reported above, with clearer messages.
        ValidationHelper.findSchemaViolations(merged, schema)
            .filter { it.keyword != "additionalProperties" && it.keyword != "required" }
            .forEach { violation ->
                errors += McpWorkflowIssue(
                    "Node '$key' properties: ${violation.path}: ${violation.message}",
                    node = key,
                    property = violation.property
                )
            }
    }

    /** Returns the edges whose endpoints and ports all exist, so the cycle check and warnings only see real connections. */
    private fun validateEdges(
        edges: List<McpWorkflowSpecEdge>,
        byKey: Map<String, ResolvedNode>,
        errors: MutableList<McpWorkflowIssue>
    ): List<McpWorkflowSpecEdge> {
        val valid = mutableListOf<McpWorkflowSpecEdge>()
        val seen = mutableSetOf<String>()
        val inputFedBy = mutableMapOf<String, String>()
        val knownKeys = byKey.keys.joinToString(", ").ifEmpty { "(none)" }

        edges.forEachIndexed { index, edge ->
            val label = "${edge.from}.${edge.fromPort} -> ${edge.to}.${edge.toPort}"
            if (edge.from.isNullOrBlank() || edge.fromPort.isNullOrBlank() || edge.to.isNullOrBlank() || edge.toPort.isNullOrBlank()) {
                errors += McpWorkflowIssue("Edge #${index + 1} ($label) needs from, fromPort, to and toPort.", edge = label)
                return@forEachIndexed
            }
            var ok = true
            val source = byKey[edge.from]
            val target = byKey[edge.to]
            if (source == null) {
                errors += McpWorkflowIssue("Edge $label: source node key '${edge.from}' does not exist. Node keys: $knownKeys.", edge = label)
                ok = false
            }
            if (target == null) {
                errors += McpWorkflowIssue("Edge $label: target node key '${edge.to}' does not exist. Node keys: $knownKeys.", edge = label)
                ok = false
            }
            if (edge.from == edge.to) {
                errors += McpWorkflowIssue("Edge $label: a node cannot connect to itself.", edge = label)
                ok = false
            }
            source?.metadata?.let { metadata ->
                val outputs = metadata.outputs.orEmpty().keys
                if (edge.fromPort !in outputs) {
                    errors += McpWorkflowIssue(
                        "Edge $label: '${source.nodeId}' has no output port '${edge.fromPort}'. " +
                            "Output ports: ${outputs.joinToString(", ").ifEmpty { "(none)" }}.",
                        edge = label
                    )
                    ok = false
                }
            }
            target?.metadata?.let { metadata ->
                val inputs = metadata.inputs.orEmpty().keys
                if (edge.toPort !in inputs) {
                    errors += McpWorkflowIssue(
                        "Edge $label: '${target.nodeId}' has no input port '${edge.toPort}'. " +
                            "Input ports: ${inputs.joinToString(", ").ifEmpty { "(none)" }}.",
                        edge = label
                    )
                    ok = false
                }
            }
            if (!seen.add(label)) {
                errors += McpWorkflowIssue("Edge $label is listed more than once.", edge = label)
                return@forEachIndexed
            }
            val input = "${edge.to}.${edge.toPort}"
            val previous = inputFedBy.putIfAbsent(input, "${edge.from}.${edge.fromPort}")
            if (previous != null) {
                errors += McpWorkflowIssue(
                    "Edge $label: input port '${edge.toPort}' of node '${edge.to}' is already fed by $previous. " +
                        "Each input port takes exactly one edge.",
                    edge = label
                )
                ok = false
            }
            if (ok) valid += edge
        }
        return valid
    }

    /** Kahn's algorithm; returns the keys left over when the graph has a cycle, else an empty list. */
    private fun findCycle(keys: Set<String>, edges: List<McpWorkflowSpecEdge>): List<String> {
        val indegree = keys.associateWith { 0 }.toMutableMap()
        val outgoing = keys.associateWith { mutableListOf<String>() }
        for (edge in edges) {
            outgoing.getValue(edge.from!!).add(edge.to!!)
            indegree[edge.to] = indegree.getValue(edge.to) + 1
        }
        val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys)
        while (queue.isNotEmpty()) {
            for (next in outgoing.getValue(queue.removeFirst())) {
                indegree[next] = indegree.getValue(next) - 1
                if (indegree[next] == 0) queue.addLast(next)
            }
        }
        return indegree.filterValues { it > 0 }.keys.toList()
    }

    private fun collectConnectionWarnings(
        byKey: Map<String, ResolvedNode>,
        edges: List<McpWorkflowSpecEdge>,
        warnings: MutableList<McpWorkflowIssue>
    ) {
        for (node in byKey.values) {
            val connected = edges.any { it.from == node.key || it.to == node.key }
            if (!connected && byKey.size > 1) {
                warnings += McpWorkflowIssue("Node '${node.key}' is not connected to any other node.", node = node.key)
            }
            val fed = edges.filter { it.to == node.key }.map { it.toPort }.toSet()
            for (port in node.metadata?.inputs.orEmpty().keys - fed) {
                warnings += McpWorkflowIssue(
                    "Node '${node.key}': input port '$port' has no incoming edge. The node may fail if it needs this input.",
                    node = node.key
                )
            }
        }
    }
}
