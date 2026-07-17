package org.projectcontinuum.core.api.server.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

enum class NodeTreeEntryType {
  CATEGORY,
  CONTINUUM_NODE
}

@Table("node_tree_entries")
data class NodeTreeEntryEntity(
  @Id
  val id: Long? = null,
  @Column("parent_id")
  val parentId: Long? = null,
  @Column("type")
  val type: NodeTreeEntryType,
  @Column("name")
  val name: String,
  @Column("node_id")
  val nodeId: String? = null,
  @Column("task_queue")
  val taskQueue: String? = null,
  @Column("worker_id")
  val workerId: String? = null,
  @Column("feature_id")
  val featureId: String? = null,
  @Column("node_manifest")
  val nodeManifest: String? = null,
  @Column("documentation_markdown")
  val documentationMarkdown: String? = null,
  @Column("extensions")
  val extensions: String? = "{}",
  @Column("registered_at")
  val registeredAt: Instant? = null,
  @Column("last_seen_at")
  val lastSeenAt: Instant? = null
)
