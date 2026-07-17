package org.projectcontinuum.core.api.server.service

import tools.jackson.databind.ObjectMapper
import org.projectcontinuum.core.api.server.entity.NodeTreeEntryEntity
import org.projectcontinuum.core.api.server.entity.NodeTreeEntryType
import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
import org.projectcontinuum.core.api.server.repository.jdbc.NodeTreeEntryRepository
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NodeExplorerService(
  private val repository: NodeTreeEntryRepository,
  private val objectMapper: ObjectMapper
) {

  private val log = LoggerFactory.getLogger(NodeExplorerService::class.java)

  fun getChildren(parentId: String): List<NodeExplorerTreeItem> {
    val rows = if (parentId.isBlank()) {
      repository.findRootChildren()
    } else {
      val resolvedId = resolvePathToId(parentId) ?: return emptyList()
      repository.findChildrenByParentId(resolvedId)
    }
    return rows.map { toTreeItem(it, parentId) }
  }

  fun search(query: String): List<NodeExplorerTreeItem> {
    if (query.isBlank()) return emptyList()
    val pattern = "%${query}%"
    val matches = repository.searchNodes(pattern)

    val root = mutableListOf<NodeExplorerTreeItem>()
    val categoryByEntryId = mutableMapOf<Long, NodeExplorerTreeItem>()

    for (match in matches) {
      var insertionList = root
      for (ancestor in walkAncestorsRootFirst(match.parentId)) {
        val category = categoryByEntryId.getOrPut(ancestor.id!!) {
          NodeExplorerTreeItem(
            id = ancestor.path,
            name = ancestor.entity.name,
            hasChildren = true,
            type = NodeExplorerItemType.CATEGORY,
            children = mutableListOf()
          ).also { insertionList.add(it) }
        }
        insertionList = category.children!!
      }
      insertionList.add(toTreeItem(match, parentPath = ""))
    }

    return root
  }

  fun getDocumentation(nodeId: String): String? {
    log.info("Requesting documentation for nodeId='{}' (length={})", nodeId, nodeId.length)
    val entity = repository.findByNodeId(nodeId)
    if (entity == null) {
      log.warn("No registered node found for nodeId='{}'", nodeId)
      return null
    }
    val doc = entity.documentationMarkdown
    if (doc.isNullOrBlank() || doc.startsWith("Documentation not found for")) {
      log.warn("No documentation available for nodeId='{}'", nodeId)
      return null
    }
    return doc
  }

  fun getTaskQueues(nodeIds: List<String>): Map<String, String> {
    if (nodeIds.isEmpty()) return emptyMap()
    return repository.findByNodeIdIn(nodeIds)
      .associate { it.nodeId!! to it.taskQueue!! }
  }

  private fun resolvePathToId(path: String): Long? {
    var parentId: Long? = null
    for (segment in path.split("/")) {
      parentId = repository.findCategoryIdByParentAndName(parentId, segment) ?: return null
    }
    return parentId
  }

  private data class AncestorRow(val id: Long, val path: String, val entity: NodeTreeEntryEntity)

  private fun walkAncestorsRootFirst(startParentId: Long?): List<AncestorRow> {
    val chainLeafFirst = mutableListOf<NodeTreeEntryEntity>()
    var currentId = startParentId
    while (currentId != null) {
      val row = repository.findById(currentId).orElse(null) ?: break
      chainLeafFirst.add(row)
      currentId = row.parentId
    }
    val chainRootFirst = chainLeafFirst.reversed()
    val result = mutableListOf<AncestorRow>()
    var path = ""
    for (row in chainRootFirst) {
      path = if (path.isEmpty()) row.name else "$path/${row.name}"
      result.add(AncestorRow(id = row.id!!, path = path, entity = row))
    }
    return result
  }

  private fun toTreeItem(row: NodeTreeEntryEntity, parentPath: String): NodeExplorerTreeItem = when (row.type) {
    NodeTreeEntryType.CATEGORY -> NodeExplorerTreeItem(
      id = if (parentPath.isBlank()) row.name else "$parentPath/${row.name}",
      name = row.name,
      hasChildren = true,
      type = NodeExplorerItemType.CATEGORY,
      children = null
    )
    NodeTreeEntryType.CONTINUUM_NODE -> NodeExplorerTreeItem(
      id = row.nodeId!!,
      name = row.name,
      nodeInfo = objectMapper.readValue(row.nodeManifest!!, ContinuumWorkflowModel.NodeData::class.java),
      hasChildren = false,
      type = NodeExplorerItemType.NODE,
      children = null
    )
  }
}
