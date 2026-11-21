package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

@Schema(name = "NodeExplorerItem", description = "A category or a node in the node palette.")
data class NodeExplorerTreeItem(
  @field:Schema(
    description = "For a category: its path from the root (`Analytics/Text`), the `parentId` to list its children. " +
      "For a node: the node id (fully qualified class name).",
    example = "org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel"
  )
  val id: String,
  @field:Schema(description = "Display name.", example = "Text Normalizer")
  val name: String,
  @field:Schema(description = "For a node: its registered definition (ports, default properties, schemas). Null for categories.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
  val nodeInfo: ContinuumWorkflowModel.NodeData? = null,
  @field:Schema(description = "Whether a category has children; always false for nodes.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "false", example = "false")
  val hasChildren: Boolean = false,
  @field:Schema(description = "Whether this is a category or a node.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "CATEGORY", example = "NODE")
  val type: NodeExplorerItemType = NodeExplorerItemType.CATEGORY,
  @field:Schema(description = "Children, filled only in search results (the ancestors of each match). Null when not loaded.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
  val children: MutableList<NodeExplorerTreeItem>? = null
)

@Schema(name = "NodeExplorerItemType", description = "`CATEGORY`: a folder of the palette. `NODE`: a registered node type.")
enum class NodeExplorerItemType {
    CATEGORY,
    NODE
}
