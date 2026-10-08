package org.projectcontinuum.core.cluster.manager.model

data class FeatureWorkerCreateRequest(
  val workerName: String,
  val image: String,
  val taskQueue: String,
  val resources: FeatureWorkerResourceSpec? = null,
  val replicas: Int = 1,
  val autoscaling: AutoscalingSpec? = null,
  val pvcEnabled: Boolean = false,
  val envVars: Map<String, String> = emptyMap(),
  val variant: String? = null
) {
  fun resolvedResources(): FeatureWorkerResourceSpec = resources ?: FeatureWorkerResourceSpec()
  fun resolvedAutoscaling(): AutoscalingSpec = autoscaling ?: AutoscalingSpec()
}
