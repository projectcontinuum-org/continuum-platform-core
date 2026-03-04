package com.continuum.core.api.server.model

import com.continuum.core.commons.model.ContinuumWorkflowModel

data class NodeExplorerTreeItem(
    val id: String,
    val name: String,
    val nodeInfo: ContinuumWorkflowModel.NodeData? = null,
    val hasChildren: Boolean = false,
    val type: NodeExplorerItemType = NodeExplorerItemType.CATEGORY
)

enum class NodeExplorerItemType {
    CATEGORY,
    NODE
}
