package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.springframework.ai.mcp.annotation.McpResource
import org.springframework.stereotype.Component

@Component
class ContinuumNodeMcpResources(
    private val nodeExplorerService: NodeExplorerService
) {

    @McpResource(
        uri = "continuum-node://{nodeId}/documentation",
        name = "Continuum node documentation",
        description = "Markdown documentation for a registered Continuum node",
        mimeType = "text/markdown"
    )
    fun getNodeDocumentation(nodeId: String): String {
        return nodeExplorerService.getDocumentation(nodeId)
            ?: "No documentation available for node '$nodeId'."
    }
}
