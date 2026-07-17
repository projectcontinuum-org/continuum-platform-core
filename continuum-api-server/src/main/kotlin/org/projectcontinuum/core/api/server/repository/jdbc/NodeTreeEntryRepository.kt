package org.projectcontinuum.core.api.server.repository.jdbc

import org.projectcontinuum.core.api.server.entity.NodeTreeEntryEntity
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface NodeTreeEntryRepository : CrudRepository<NodeTreeEntryEntity, Long> {

  @Query("""
    SELECT * FROM node_tree_entries
    WHERE parent_id IS NULL
    ORDER BY type = 'CONTINUUM_NODE', name
  """)
  fun findRootChildren(): List<NodeTreeEntryEntity>

  @Query("""
    SELECT * FROM node_tree_entries
    WHERE parent_id = :parentId
    ORDER BY type = 'CONTINUUM_NODE', name
  """)
  fun findChildrenByParentId(parentId: Long): List<NodeTreeEntryEntity>

  @Query("""
    SELECT id FROM node_tree_entries
    WHERE type = 'CATEGORY' AND name = :name
      AND ((:parentId IS NULL AND parent_id IS NULL) OR parent_id = :parentId)
  """)
  fun findCategoryIdByParentAndName(parentId: Long?, name: String): Long?

  @Query("""
    SELECT * FROM node_tree_entries
    WHERE type = 'CONTINUUM_NODE'
      AND (name ILIKE :pattern
       OR node_manifest->>'description' ILIKE :pattern
       OR documentation_markdown ILIKE :pattern
       OR node_id ILIKE :pattern)
  """)
  fun searchNodes(pattern: String): List<NodeTreeEntryEntity>

  @Query("""
    SELECT * FROM node_tree_entries
    WHERE type = 'CONTINUUM_NODE' AND node_id = :nodeId
    LIMIT 1
  """)
  fun findByNodeId(nodeId: String): NodeTreeEntryEntity?

  @Query("""
    SELECT * FROM node_tree_entries
    WHERE type = 'CONTINUUM_NODE' AND node_id IN (:nodeIds)
  """)
  fun findByNodeIdIn(nodeIds: List<String>): List<NodeTreeEntryEntity>
}
