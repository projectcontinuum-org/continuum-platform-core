package org.projectcontinuum.core.cluster.manager.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
  name = "WorkbenchResources",
  description = "Compute and storage of a workbench. Values are Kubernetes quantities (`500m`, `2`, `512Mi`, `5Gi`; at most " +
    "32 characters). In a create request, omitted fields take their defaults; in an update, they keep their value."
)
data class ResourceSpec(
  @field:Schema(description = "CPU request (Kubernetes quantity, cores or millicores).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "500m", example = "500m")
  val cpuRequest: String = "500m",
  @field:Schema(description = "CPU limit (Kubernetes quantity).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "2", example = "2")
  val cpuLimit: String = "2",
  @field:Schema(description = "Memory request (Kubernetes quantity, bytes with binary suffix).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "512Mi", example = "512Mi")
  val memoryRequest: String = "512Mi",
  @field:Schema(description = "Memory limit (Kubernetes quantity).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "1Gi", example = "2Gi")
  val memoryLimit: String = "1Gi",
  @field:Schema(description = "Size of the persistent volume holding the user's home and workspace (Kubernetes quantity).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "5Gi", example = "10Gi")
  val storageSize: String = "5Gi",
  @field:Schema(description = "Storage class of the volume (DNS-1123 subdomain); null for the cluster default.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "gp3")
  val storageClassName: String? = null
)
