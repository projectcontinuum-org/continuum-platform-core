package org.projectcontinuum.core.api.server.model

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

data class NodeExplorerTreeItem(
    val id: String,
    val name: String,
    val nodeInfo: ContinuumWorkflowModel.NodeData? = null,
    val hasChildren: Boolean = false,
    val type: NodeExplorerItemType = NodeExplorerItemType.CATEGORY,
    val children: MutableList<NodeExplorerTreeItem>? = null
)

enum class NodeExplorerItemType {
    CATEGORY,
    NODE
}
