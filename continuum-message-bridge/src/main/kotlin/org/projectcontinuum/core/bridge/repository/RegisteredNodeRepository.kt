package org.projectcontinuum.core.bridge.repository

import org.projectcontinuum.core.bridge.entity.RegisteredNodeEntity
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface RegisteredNodeRepository : CrudRepository<RegisteredNodeEntity, Long> {

  fun findByNodeIdAndTaskQueue(nodeId: String, taskQueue: String): RegisteredNodeEntity?

  @Modifying
  @Query("""
    INSERT INTO registered_nodes (node_id, task_queue, worker_id, feature_id, node_manifest, documentation_markdown, extensions, registered_at, last_seen_at)
    VALUES (:nodeId, :taskQueue, :workerId, :featureId, CAST(:nodeManifest AS JSONB), :documentationMarkdown, CAST(:extensions AS JSONB), :registeredAt, :lastSeenAt)
    ON CONFLICT (node_id, task_queue) DO UPDATE SET
      worker_id = :workerId,
      feature_id = :featureId,
      node_manifest = CAST(:nodeManifest AS JSONB),
      documentation_markdown = :documentationMarkdown,
      extensions = CAST(:extensions AS JSONB),
      registered_at = :registeredAt,
      last_seen_at = :lastSeenAt
  """)
  fun upsert(
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
}
