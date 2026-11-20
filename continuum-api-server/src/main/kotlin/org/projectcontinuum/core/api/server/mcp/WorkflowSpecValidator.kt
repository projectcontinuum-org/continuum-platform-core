package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.validation.GraphEdge
import org.projectcontinuum.core.commons.validation.GraphNode
import org.projectcontinuum.core.commons.validation.ValidationMode
import org.projectcontinuum.core.commons.validation.WorkflowIssue
import org.projectcontinuum.core.commons.validation.WorkflowValidator
import org.springframework.stereotype.Component

/**
 * Checks a compact workflow spec against the live node registry. The checks themselves live in
 * [WorkflowValidator] (`continuum-commons`), shared with the start endpoint; this class adds the spec-only rules (a name
 * is required, an empty node list is a warning) and maps the result to the MCP types. Every problem is collected, not
 * just the first, so a caller can fix them all in one pass.
 */
@Component
class WorkflowSpecValidator(
    nodeExplorerService: NodeExplorerService
) {

    private val validator = WorkflowValidator { nodeExplorerService.getNodeMetadata(it) }

    fun validate(name: String?, nodes: List<McpWorkflowSpecNode>?, edges: List<McpWorkflowSpecEdge>?): McpWorkflowValidationResult {
        val specNodes = nodes.orEmpty()
        val specEdges = edges.orEmpty()

        val errors = mutableListOf<McpWorkflowIssue>()
        val warnings = mutableListOf<McpWorkflowIssue>()
        if (name.isNullOrBlank()) errors += McpWorkflowIssue(message = "The workflow needs a non-empty name.")
        // Allowed so a client can save an empty workflow first and fill it in node by node
        if (specNodes.isEmpty()) warnings += McpWorkflowIssue(message = "The workflow has no nodes yet.")

        val result = validator.validate(
            nodes = specNodes.map { GraphNode(key = it.key, nodeId = it.nodeId, properties = it.properties) },
            edges = specEdges.map { GraphEdge(from = it.from, fromPort = it.fromPort, to = it.to, toPort = it.toPort) },
            mode = ValidationMode.AUTHORING
        )
        errors += result.errors.map { it.toMcp() }
        warnings += result.warnings.map { it.toMcp() }

        return McpWorkflowValidationResult(
            valid = errors.isEmpty(),
            nodeCount = specNodes.size,
            edgeCount = specEdges.size,
            errors = errors,
            warnings = warnings
        )
    }

    private fun WorkflowIssue.toMcp() = McpWorkflowIssue(message = message, node = node, edge = edge, property = property)
}
