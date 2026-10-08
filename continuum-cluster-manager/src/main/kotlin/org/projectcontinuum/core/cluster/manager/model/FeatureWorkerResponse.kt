package org.projectcontinuum.core.cluster.manager.model

import java.time.Instant
import java.util.UUID

data class FeatureWorkerResponse(
  val workerId: UUID,
  val workerName: String,
  val namespace: String,
  val createdBy: String,
  val status: String,
  val image: String,
  val taskQueue: String,
  val replicas: Int,
  val autoscaling: AutoscalingSpec,
  val resources: FeatureWorkerResourceSpec,
  val pvcEnabled: Boolean,
  val envVars: Map<String, String>,
  val overlayVariant: String?,
  val createdAt: Instant,
  val updatedAt: Instant
)
