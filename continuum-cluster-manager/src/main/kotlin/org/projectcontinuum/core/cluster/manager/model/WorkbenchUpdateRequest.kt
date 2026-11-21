package org.projectcontinuum.core.cluster.manager.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "WorkbenchUpdateRequest", description = "Changes to a workbench; omitted fields keep their value.")
data class WorkbenchUpdateRequest(
  @field:Schema(description = "New compute and storage values; only the given fields change.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
  val resources: ResourceUpdate? = null,
  @field:Schema(description = "New container image (must start with an allowed image prefix).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "projectcontinuum/continuum-workbench:0.0.15")
  val image: String? = null
)

/**
 * Resource changes of an update. Unlike [ResourceSpec] (a create, where omitted fields take the defaults), every field
 * is optional with no default: an omitted field keeps the workbench's current value, so changing only `memoryLimit`
 * cannot reset the others.
 */
@Schema(name = "WorkbenchResourcesUpdate", description = "Resource changes; omitted fields keep their current value. Values are Kubernetes quantities.")
data class ResourceUpdate(
  @field:Schema(description = "New CPU request.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "1")
  val cpuRequest: String? = null,
  @field:Schema(description = "New CPU limit.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "4")
  val cpuLimit: String? = null,
  @field:Schema(description = "New memory request.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "1Gi")
  val memoryRequest: String? = null,
  @field:Schema(description = "New memory limit.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "4Gi")
  val memoryLimit: String? = null,
  @field:Schema(description = "New volume size. Kubernetes can usually only grow a volume, and only with an expandable storage class.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "20Gi")
  val storageSize: String? = null,
  @field:Schema(description = "New storage class (DNS-1123 subdomain).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "gp3")
  val storageClassName: String? = null
)
