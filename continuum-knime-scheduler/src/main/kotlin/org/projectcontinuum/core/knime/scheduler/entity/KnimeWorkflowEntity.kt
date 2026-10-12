package org.projectcontinuum.core.knime.scheduler.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "knime_workflows")
class KnimeWorkflowEntity(

  @Id
  @Column(name = "workflow_id")
  val workflowId: UUID,

  @Column(name = "owned_by", nullable = false)
  val ownedBy: String,

  @Column(name = "file_name", nullable = false)
  var fileName: String,

  @Column(name = "object_key", nullable = false, columnDefinition = "VARCHAR(2048)")
  val objectKey: String,

  @Column(name = "bucket_name", nullable = false)
  val bucketName: String,

  @Column(name = "size_bytes", nullable = false)
  var sizeBytes: Long,

  @Column(name = "content_type")
  var contentType: String? = null,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant = Instant.now(),

  @Column(name = "updated_at", nullable = false)
  var updatedAt: Instant = Instant.now()
)
