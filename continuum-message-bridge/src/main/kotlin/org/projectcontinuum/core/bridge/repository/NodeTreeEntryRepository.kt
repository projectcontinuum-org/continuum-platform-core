package org.projectcontinuum.core.bridge.repository

import org.projectcontinuum.core.bridge.entity.NodeTreeEntryEntity
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface NodeTreeEntryRepository : CrudRepository<NodeTreeEntryEntity, Long> {

  @Query("""
    SELECT id FROM node_tree_entries
    WHERE type = 'CATEGORY' AND name = :name
      AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
  """)
  fun findCategoryIdByParentAndName(parentId: Long?, name: String): Long?

  @Query("""
    INSERT INTO node_tree_entries (parent_id, type, name)
    VALUES (:parentId, 'CATEGORY', :name)
    ON CONFLICT (COALESCE(parent_id, -1), name) WHERE type = 'CATEGORY' DO NOTHING
    RETURNING id
  """)
  fun insertCategoryIfAbsent(parentId: Long?, name: String): Long?

  @Query("""
    SELECT id, parent_id, name, type FROM node_tree_entries
    WHERE type = 'CONTINUUM_NODE' AND node_id = :nodeId
  """)
  fun findPlacementsByNodeId(nodeId: String): List<NodeTreeEntryEntity>

  @Modifying
  @Query("""
    INSERT INTO node_tree_entries (
      parent_id, type, name, node_id, task_queue, worker_id, feature_id,
      node_manifest, documentation_markdown, extensions, registered_at, last_seen_at
    )
    VALUES (
      :parentId, 'CONTINUUM_NODE', :name, :nodeId, :taskQueue, :workerId, :featureId,
      CAST(:nodeManifest AS JSONB), :documentationMarkdown, CAST(:extensions AS JSONB), :registeredAt, :lastSeenAt
    )
    ON CONFLICT (node_id, COALESCE(parent_id, -1)) WHERE type = 'CONTINUUM_NODE'
    DO UPDATE SET
      name = :name,
      task_queue = :taskQueue,
      worker_id = :workerId,
      feature_id = :featureId,
      node_manifest = CAST(:nodeManifest AS JSONB),
      documentation_markdown = :documentationMarkdown,
      extensions = CAST(:extensions AS JSONB),
      last_seen_at = :lastSeenAt
  """)
  fun upsertNodeEntry(
    parentId: Long?,
    name: String,
    nodeId: String,
    taskQueue: String,
    workerId: String,
    featureId: String,
    nodeManifest: String,
    documentationMarkdown: String,
    extensions: String,
    registeredAt: Instant,
    lastSeenAt: Instant
  )

  @Modifying
  @Query("""
    DELETE FROM node_tree_entries
    WHERE type = 'CONTINUUM_NODE' AND node_id = :nodeId
      AND ((:includeNullParent = TRUE AND parent_id IS NULL) OR parent_id = ANY(:nonNullStaleParentIds))
  """)
  fun deleteStalePlacements(nodeId: String, nonNullStaleParentIds: LongArray, includeNullParent: Boolean)

  @Query("""
    SELECT COUNT(*) FROM node_tree_entries WHERE parent_id = :categoryId
  """)
  fun countChildren(categoryId: Long): Long

  @Query("""
    SELECT parent_id FROM node_tree_entries WHERE id = :categoryId
  """)
  fun findParentId(categoryId: Long): Long?

  @Modifying
  @Query("""
    DELETE FROM node_tree_entries WHERE id = :categoryId AND type = 'CATEGORY'
  """)
  fun deleteCategoryById(categoryId: Long)
}
