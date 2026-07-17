package org.projectcontinuum.core.bridge.service

import org.projectcontinuum.core.bridge.repository.NodeTreeEntryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class NodeTreeSyncService(
  private val repository: NodeTreeEntryRepository
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(NodeTreeSyncService::class.java)
  }

  @Transactional
  fun sync(
    nodeId: String,
    name: String,
    taskQueue: String,
    workerId: String,
    featureId: String,
    nodeManifest: String,
    documentationMarkdown: String,
    categories: List<String>,
    extensions: String,
    registeredAt: Instant,
    lastSeenAt: Instant
  ) {
    val oldPlacements = repository.findPlacementsByNodeId(nodeId)
    val oldParentIds = oldPlacements.map { it.parentId }.toSet()

    val distinctPaths = categories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val desiredParentIds: Set<Long?> = if (distinctPaths.isEmpty()) {
      setOf(null)
    } else {
      distinctPaths.map { resolveOrCreateCategoryChain(it) }.toSet()
    }

    for (parentId in desiredParentIds) {
      repository.upsertNodeEntry(
        parentId = parentId,
        name = name,
        nodeId = nodeId,
        taskQueue = taskQueue,
        workerId = workerId,
        featureId = featureId,
        nodeManifest = nodeManifest,
        documentationMarkdown = documentationMarkdown,
        extensions = extensions,
        registeredAt = registeredAt,
        lastSeenAt = lastSeenAt
      )
    }

    val staleParentIds = oldParentIds - desiredParentIds
    if (staleParentIds.isNotEmpty()) {
      val nonNullStale = staleParentIds.filterNotNull().toLongArray()
      val includeNullParent = staleParentIds.contains(null)
      repository.deleteStalePlacements(nodeId, nonNullStale, includeNullParent)
      pruneEmptyAncestors(staleParentIds.filterNotNull().toSet())
    }
  }

  private fun resolveOrCreateCategoryChain(path: String): Long {
    var parentId: Long? = null
    for (segment in path.split("/").map { it.trim() }.filter { it.isNotEmpty() }) {
      parentId = repository.insertCategoryIfAbsent(parentId, segment)
        ?: repository.findCategoryIdByParentAndName(parentId, segment)
        ?: error("Failed to resolve or create category '$segment' under parent $parentId")
    }
    return parentId ?: error("Category path '$path' resolved to no segments")
  }

  private fun pruneEmptyAncestors(seed: Set<Long>) {
    var candidates = seed
    while (candidates.isNotEmpty()) {
      val next = mutableSetOf<Long>()
      for (categoryId in candidates) {
        if (repository.countChildren(categoryId) == 0L) {
          val parentId = repository.findParentId(categoryId)
          repository.deleteCategoryById(categoryId)
          LOGGER.debug("Pruned empty category id={}", categoryId)
          if (parentId != null) next.add(parentId)
        }
      }
      candidates = next
    }
  }
}
