package org.projectcontinuum.core.cluster.manager.model

data class ResourceSpec(
  val cpuRequest: String = "500m",
  val cpuLimit: String = "2",
  val memoryRequest: String = "512Mi",
  val memoryLimit: String = "1Gi",
  val storageSize: String = "5Gi",
  val storageClassName: String? = null
)
