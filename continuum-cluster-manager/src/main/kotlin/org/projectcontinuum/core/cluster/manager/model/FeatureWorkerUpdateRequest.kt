package org.projectcontinuum.core.cluster.manager.model

data class FeatureWorkerUpdateRequest(
  val image: String? = null,
  val taskQueue: String,
  val resources: FeatureWorkerResourceSpec? = null,
  val replicas: Int? = null,
  val autoscaling: AutoscalingSpec? = null,
  val pvcEnabled: Boolean? = null,
  val envVars: Map<String, String>? = null
)
