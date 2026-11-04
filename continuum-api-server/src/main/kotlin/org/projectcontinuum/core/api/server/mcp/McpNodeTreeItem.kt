package org.projectcontinuum.core.api.server.mcp

import com.fasterxml.jackson.annotation.JsonInclude
import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem

/**
 * Compact registry entry returned by the list and search MCP tools. Schemas, defaults and icons are left out to keep
 * results small for language models; getContinuumNodeMetadata returns the full node.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpNodeTreeItem(
    val type: NodeExplorerItemType,
    val id: String,
    val name: String,
    val description: String? = null,
    val inputs: List<String>? = null,
    val outputs: List<String>? = null,
    val children: List<McpNodeTreeItem>? = null
) {
    companion object {
        fun from(item: NodeExplorerTreeItem): McpNodeTreeItem {
            val info = item.nodeInfo
            return McpNodeTreeItem(
                type = item.type,
                id = item.id,
                name = item.name,
                description = info?.description,
                inputs = info?.inputs?.keys?.toList(),
                outputs = info?.outputs?.keys?.toList(),
                children = item.children?.map { from(it) }
            )
        }
    }
}
