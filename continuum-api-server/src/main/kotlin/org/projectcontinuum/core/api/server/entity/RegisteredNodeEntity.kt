package org.projectcontinuum.core.api.server.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("registered_nodes")
data class RegisteredNodeEntity(
  @Id
  val id: Long? = null,
  @Column("node_id")
  val nodeId: String,
  @Column("task_queue")
  val taskQueue: String,
  @Column("worker_id")
  val workerId: String,
  @Column("feature_id")
  val featureId: String,
  @Column("node_manifest")
  val nodeManifest: String,
  @Column("documentation_markdown")
  val documentationMarkdown: String,
  @Column("categories")
  val categories: String = "[]",
  @Column("extensions")
  val extensions: String = "{}",
  @Column("registered_at")
  val registeredAt: Instant,
  @Column("last_seen_at")
  val lastSeenAt: Instant
)
