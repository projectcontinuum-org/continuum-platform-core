package org.projectcontinuum.core.api.server.repository

import org.projectcontinuum.core.api.server.entity.RegisteredNodeEntity
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface RegisteredNodeRepository : CrudRepository<RegisteredNodeEntity, Long> {

  @Query("""
    SELECT DISTINCT jsonb_array_elements_text(categories) AS category
    FROM registered_nodes
    ORDER BY category
  """)
  fun findAllDistinctCategories(): List<String>

  @Query("""
    SELECT * FROM registered_nodes
    WHERE categories @> CAST(:category AS JSONB)
  """)
  fun findByCategoriesContaining(category: String): List<RegisteredNodeEntity>

  @Query("""
    SELECT * FROM registered_nodes
    WHERE categories = '[]'::jsonb
  """)
  fun findByEmptyCategories(): List<RegisteredNodeEntity>

  @Query("""
    SELECT * FROM registered_nodes
    WHERE node_manifest->>'title' ILIKE :pattern
       OR node_manifest->>'description' ILIKE :pattern
       OR documentation_markdown ILIKE :pattern
       OR node_id ILIKE :pattern
  """)
  fun searchNodes(pattern: String): List<RegisteredNodeEntity>

  @Query("""
    SELECT documentation_markdown FROM registered_nodes
    WHERE node_id = :nodeId
    LIMIT 1
  """)
  fun findDocumentationByNodeId(nodeId: String): String?
}
