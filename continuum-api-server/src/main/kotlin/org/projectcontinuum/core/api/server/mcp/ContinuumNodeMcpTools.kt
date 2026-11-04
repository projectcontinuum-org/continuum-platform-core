package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class ContinuumNodeMcpTools(
    private val nodeExplorerService: NodeExplorerService
) {

    @McpTool(
        name = "listContinuumNodeCategories",
        description = "List the categories and nodes directly under a category path in the Continuum node registry. " +
            "Pass an empty parentId to list the top-level categories, then pass a category id from an earlier result to go deeper. " +
            "Nodes are returned in compact form (id, name, description, port names); call getContinuumNodeMetadata for the full details."
    )
    fun listContinuumNodeCategories(
        @McpToolParam(
            description = "Category path to list, exactly as returned in the id of an earlier category result, e.g. 'RDKit/Rendering'. " +
                "Empty string for the root.",
            required = false
        )
        parentId: String?
    ): List<McpNodeTreeItem> {
        return nodeExplorerService.getChildren(parentId ?: "").map { McpNodeTreeItem.from(it) }
    }

    @McpTool(
        name = "searchContinuumNodes",
        description = "Search the Continuum node registry. The query is matched case-insensitively as one literal substring of each " +
            "node's name, description, documentation and id, so use a single distinctive word (e.g. 'svg', 'smiles', 'csv'), not a sentence. " +
            "Matching nodes are returned in compact form, grouped under their category tree; call getContinuumNodeMetadata for the full details."
    )
    fun searchContinuumNodes(
        @McpToolParam(description = "A single word or short phrase to match, e.g. 'svg'", required = true)
        query: String
    ): List<McpNodeTreeItem> {
        return nodeExplorerService.search(query).map { McpNodeTreeItem.from(it) }
    }

    @McpTool(
        name = "getContinuumNodeMetadata",
        description = "Fetch the full metadata for one registered Continuum node: title, description, input/output ports, " +
            "default property values and the JSON Schema for its configurable properties."
    )
    fun getContinuumNodeMetadata(
        @McpToolParam(
            description = "The full registered node id, exactly as returned in the id of a node by listContinuumNodeCategories or " +
                "searchContinuumNodes, e.g. 'org.projectcontinuum.feature.rdkit.node.RDKit2SVGNodeModel'",
            required = true
        )
        nodeId: String
    ): ContinuumWorkflowModel.NodeData {
        return nodeExplorerService.getNodeMetadata(nodeId)
            ?: throw IllegalArgumentException(
                "No registered node with id '$nodeId'. Use the full node id exactly as returned by " +
                    "searchContinuumNodes or listContinuumNodeCategories (e.g. 'org.projectcontinuum.feature.rdkit.node.RDKit2SVGNodeModel')."
            )
    }
}
