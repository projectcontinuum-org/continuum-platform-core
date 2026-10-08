package org.projectcontinuum.core.cluster.manager.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("feature_worker_instances")
data class FeatureWorkerInstanceEntity(
  @Id
  @Column("worker_id")
  val workerId: UUID,
  @Column("worker_name")
  val workerName: String,
  @Column("namespace")
  val namespace: String,
  @Column("created_by")
  val createdBy: String,
  @Column("status")
  val status: String,
  @Column("image")
  val image: String,
  @Column("task_queue")
  val taskQueue: String,
  @Column("replicas")
  val replicas: Int = 1,
  @Column("autoscaling_enabled")
  val autoscalingEnabled: Boolean = false,
  @Column("autoscaling_min_replicas")
  val autoscalingMinReplicas: Int? = null,
  @Column("autoscaling_max_replicas")
  val autoscalingMaxReplicas: Int? = null,
  @Column("autoscaling_target_cpu_percent")
  val autoscalingTargetCpuPercent: Int? = null,
  @Column("cpu_request")
  val cpuRequest: String = "500m",
  @Column("cpu_limit")
  val cpuLimit: String = "2",
  @Column("memory_request")
  val memoryRequest: String = "512Mi",
  @Column("memory_limit")
  val memoryLimit: String = "1Gi",
  @Column("pvc_enabled")
  val pvcEnabled: Boolean = false,
  @Column("storage_size")
  val storageSize: String? = null,
  @Column("storage_class_name")
  val storageClassName: String? = null,
  @Column("env_vars")
  val envVars: String = "{}",
  @Column("overlay_variant")
  val overlayVariant: String? = null,
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
