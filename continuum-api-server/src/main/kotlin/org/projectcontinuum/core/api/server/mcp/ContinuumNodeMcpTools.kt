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
        return nodeExplorerService.getNodeMetadata(nodeId) ?: throw unknownNode(nodeId)
    }

    @McpTool(
        name = "getContinuumNodeDocumentation",
        description = "Fetch the markdown documentation of one registered Continuum node: what it does, what each property means, " +
            "its ports, how templates behave, and examples. Read it before configuring the node."
    )
    fun getContinuumNodeDocumentation(
        @McpToolParam(
            description = "The full registered node id, exactly as returned by listContinuumNodeCategories or searchContinuumNodes",
            required = true
        )
        nodeId: String
    ): String {
        return nodeExplorerService.getDocumentation(nodeId)
            ?: if (nodeExplorerService.getNodeMetadata(nodeId) == null) throw unknownNode(nodeId)
            else "Node '$nodeId' has no documentation. Rely on the property descriptions from getContinuumNodeMetadata."
    }

    private fun unknownNode(nodeId: String) = IllegalArgumentException(
        "No registered node with id '$nodeId'. Use the full node id exactly as returned by " +
            "searchContinuumNodes or listContinuumNodeCategories (e.g. 'org.projectcontinuum.feature.rdkit.node.RDKit2SVGNodeModel')."
    )
}
