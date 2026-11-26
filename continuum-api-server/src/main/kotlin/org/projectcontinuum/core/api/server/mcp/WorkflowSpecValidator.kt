package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.validation.GraphEdge
import org.projectcontinuum.core.commons.validation.GraphNode
import org.projectcontinuum.core.commons.validation.ValidationMode
import org.projectcontinuum.core.commons.validation.WorkflowIssue
import org.projectcontinuum.core.commons.validation.WorkflowValidator
import org.projectcontinuum.core.commons.validation.loop.LoopConditionValidator
import org.projectcontinuum.core.loopcondition.LoopConditionValidators
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Checks a compact workflow spec against the live node registry. The checks themselves live in
 * [WorkflowValidator] (`continuum-commons`), shared with the start endpoint; this class adds the spec-only rules (a name
 * is required, an empty node list is a warning) and maps the result to the MCP types. Every problem is collected, not
 * just the first, so a caller can fix them all in one pass.
 *
 * A spec has no React Flow `type`: a node with the built-in loop nodeId is passed as type `Loop` (the type the builder
 * gives it), every other node without a type, so only the loop rules about `parent` and edges apply. Loop conditions
 * are checked by the cel-java validator ([LoopConditionValidators.default]) unless a test injects another.
 */
@Component
class WorkflowSpecValidator(
    nodeExplorerService: NodeExplorerService,
    conditionValidator: LoopConditionValidator
) {

    /** Spring's constructor: loop conditions are checked by the cel-java validator. */
    @Autowired
    constructor(nodeExplorerService: NodeExplorerService) : this(
        nodeExplorerService = nodeExplorerService,
        conditionValidator = LoopConditionValidators.default
    )

    private val validator = WorkflowValidator(
        conditionValidator = conditionValidator,
        descriptors = { nodeModel -> nodeExplorerService.getNodeMetadata(nodeModel) }
    )

    fun validate(name: String?, nodes: List<McpWorkflowSpecNode>?, edges: List<McpWorkflowSpecEdge>?): McpWorkflowValidationResult {
        val specNodes = nodes.orEmpty()
        val specEdges = edges.orEmpty()

        val errors = mutableListOf<McpWorkflowIssue>()
        val warnings = mutableListOf<McpWorkflowIssue>()
        if (name.isNullOrBlank()) errors += McpWorkflowIssue(message = "The workflow needs a non-empty name.")
        // Allowed so a client can save an empty workflow first and fill it in node by node
        if (specNodes.isEmpty()) warnings += McpWorkflowIssue(message = "The workflow has no nodes yet.")

        val result = validator.validate(
            nodes = specNodes.map { specNode ->
                GraphNode(
                    key = specNode.key,
                    nodeId = specNode.nodeId,
                    properties = specNode.properties,
                    type = LoopNodeSpec.typeOf(nodeId = specNode.nodeId),
                    parent = specNode.parent
                )
            },
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
