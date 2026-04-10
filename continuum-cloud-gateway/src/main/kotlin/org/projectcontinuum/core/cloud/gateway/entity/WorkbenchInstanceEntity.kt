package org.projectcontinuum.core.cloud.gateway.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("workbench_instances")
data class WorkbenchInstanceEntity(
  @Id
  @Column("instance_id")
  val instanceId: UUID,
  @Column("instance_name")
  val instanceName: String,
  @Column("namespace")
  val namespace: String,
  @Column("user_id")
  val userId: String,
  @Column("status")
  val status: String,
  @Column("image")
  val image: String,
  @Column("cpu_request")
  val cpuRequest: String = "500m",
  @Column("cpu_limit")
  val cpuLimit: String = "2",
  @Column("memory_request")
  val memoryRequest: String = "512Mi",
  @Column("memory_limit")
  val memoryLimit: String = "1Gi",
  @Column("storage_size")
  val storageSize: String = "5Gi",
  @Column("storage_class_name")
  val storageClassName: String? = null,
  @Column("k8s_resources")
  val k8sResources: String = "[]",
  @Column("created_at")
  val createdAt: Instant = Instant.now(),
  @Column("updated_at")
  val updatedAt: Instant = Instant.now(),
  @Version
  @Column("entity_version")
  val entityVersion: Long? = null
)
