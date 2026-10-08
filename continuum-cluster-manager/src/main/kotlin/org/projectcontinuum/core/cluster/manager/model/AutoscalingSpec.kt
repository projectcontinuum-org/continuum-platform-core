package org.projectcontinuum.core.cluster.manager.model

data class AutoscalingSpec(
  val enabled: Boolean = false,
  val minReplicas: Int? = null,
  val maxReplicas: Int? = null,
  val targetCPUUtilizationPercentage: Int? = null
)
