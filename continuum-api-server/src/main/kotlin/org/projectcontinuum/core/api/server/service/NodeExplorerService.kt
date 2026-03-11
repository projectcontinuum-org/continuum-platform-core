package org.projectcontinuum.core.api.server.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.projectcontinuum.core.api.server.entity.RegisteredNodeEntity
import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
import org.projectcontinuum.core.api.server.repository.RegisteredNodeRepository
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.stereotype.Service

@Service
class NodeExplorerService(
  private val registeredNodeRepository: RegisteredNodeRepository
) {

  private val objectMapper = ObjectMapper().apply {
    registerModule(kotlinModule())
  }

  fun getChildren(parentId: String): List<NodeExplorerTreeItem> {
    val fullTree = buildTree(registeredNodeRepository.findAll())
    if (parentId.isBlank()) {
      return fullTree
    }
    return findSubtreeChildren(fullTree, parentId) ?: emptyList()
  }

  fun search(query: String): List<NodeExplorerTreeItem> {
    if (query.isBlank()) return emptyList()
    val pattern = "%${query}%"
    val matchingEntities = registeredNodeRepository.searchNodes(pattern)
    return buildTree(matchingEntities)
  }

  fun getDocumentation(nodeId: String): String? {
    return registeredNodeRepository.findDocumentationByNodeId(nodeId)
  }

  private fun buildTree(entities: Iterable<RegisteredNodeEntity>): List<NodeExplorerTreeItem> {
    val treeRoots = mutableListOf<NodeExplorerTreeItem>()

    for (entity in entities) {
      val nodeData: ContinuumWorkflowModel.NodeData = objectMapper.readValue(entity.nodeManifest)
      val categories: List<String> = objectMapper.readValue(entity.categories)

      if (categories.isEmpty()) {
        treeRoots.add(
          NodeExplorerTreeItem(
            id = entity.nodeId,
            name = nodeData.title,
            nodeInfo = nodeData,
            type = NodeExplorerItemType.NODE
          )
        )
      } else {
        for (category in categories) {
          val categoryPath = category.split("/")
          addNodeToTree(treeRoots, entity.nodeId, nodeData, categoryPath.toMutableList())
        }
      }
    }

    sortTree(treeRoots)
    return treeRoots
  }

  private fun findSubtreeChildren(
    items: List<NodeExplorerTreeItem>,
    targetId: String
  ): List<NodeExplorerTreeItem>? {
    for (item in items) {
      if (item.id == targetId) {
        return item.children ?: emptyList()
      }
      if (item.children != null) {
        val found = findSubtreeChildren(item.children!!, targetId)
        if (found != null) return found
      }
    }
    return null
  }

  private fun addNodeToTree(
    currentLevel: MutableList<NodeExplorerTreeItem>,
    nodeId: String,
    nodeData: ContinuumWorkflowModel.NodeData,
    categoryPath: MutableList<String>,
    parentPath: String = ""
  ) {
    if (categoryPath.isEmpty()) {
      currentLevel.add(
        NodeExplorerTreeItem(
          id = nodeId,
          name = nodeData.title,
          nodeInfo = nodeData,
          type = NodeExplorerItemType.NODE
        )
      )
    } else {
      val categoryName = categoryPath.removeAt(0)
      val fullPath = if (parentPath.isEmpty()) categoryName else "$parentPath/$categoryName"
      var categoryNode = currentLevel.find { it.type == NodeExplorerItemType.CATEGORY && it.name == categoryName }

      if (categoryNode == null) {
        categoryNode = NodeExplorerTreeItem(
          id = fullPath,
          name = categoryName,
          hasChildren = true,
          type = NodeExplorerItemType.CATEGORY,
          children = mutableListOf()
        )
        currentLevel.add(categoryNode)
      }

      addNodeToTree(categoryNode.children!!, nodeId, nodeData, categoryPath, fullPath)
    }
  }

  private fun sortTree(items: MutableList<NodeExplorerTreeItem>) {
    items.sortWith(
      compareBy<NodeExplorerTreeItem> { it.type != NodeExplorerItemType.CATEGORY }
        .thenBy { it.name }
    )
    items.forEach { if (it.children != null) sortTree(it.children!!) }
  }
}
