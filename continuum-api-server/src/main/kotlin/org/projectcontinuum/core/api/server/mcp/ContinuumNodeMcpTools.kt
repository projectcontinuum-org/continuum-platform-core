package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
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
            "Pass an empty parentId to list the top-level categories."
    )
    fun listContinuumNodeCategories(
        @McpToolParam(description = "Category path to list children of, e.g. 'Analytics/Text'. Empty string for the root.", required = false)
        parentId: String?
    ): List<NodeExplorerTreeItem> {
        return nodeExplorerService.getChildren(parentId ?: "")
    }

    @McpTool(
        name = "searchContinuumNodes",
        description = "Search the Continuum node registry by name, returning matching nodes grouped under their category tree."
    )
    fun searchContinuumNodes(
        @McpToolParam(description = "Search text to match against registered node names", required = true)
        query: String
    ): List<NodeExplorerTreeItem> {
        return nodeExplorerService.search(query)
    }

    @McpTool(
        name = "getContinuumNodeMetadata",
        description = "Fetch the full metadata for one registered Continuum node: title, description, input/output ports, " +
            "and the JSON Schema for its configurable properties."
    )
    fun getContinuumNodeMetadata(
        @McpToolParam(
            description = "The registered node id, as returned by listContinuumNodeCategories or searchContinuumNodes",
            required = true
        )
        nodeId: String
    ): ContinuumWorkflowModel.NodeData? {
        return nodeExplorerService.getNodeMetadata(nodeId)
    }
}
