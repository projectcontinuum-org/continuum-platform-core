package org.projectcontinuum.core.knime.scheduler.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "knime_workflow_executions")
class KnimeWorkflowExecutionEntity(

  @Id
  @Column(name = "execution_id")
  val executionId: UUID,

  @Column(name = "workflow_id", nullable = false)
  val workflowId: UUID,

  @Column(name = "owned_by", nullable = false)
  val ownedBy: String,

  @Column(name = "file_name", nullable = false)
  val fileName: String,

  @Column(name = "object_key", nullable = false, columnDefinition = "VARCHAR(2048)")
  val objectKey: String,

  @Column(name = "bucket_name", nullable = false)
  val bucketName: String,

  @Column(name = "size_bytes", nullable = false)
  val sizeBytes: Long,

  @Column(name = "content_type")
  val contentType: String? = null,

  @Column(name = "status", nullable = false)
  val status: String,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant = Instant.now()
)
