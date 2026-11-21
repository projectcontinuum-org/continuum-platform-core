package org.projectcontinuum.core.cluster.manager.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "WorkbenchCreateRequest", description = "A new workbench for the caller.")
data class WorkbenchCreateRequest(
  @field:Schema(
    description = "Name of the workbench, unique among the caller's active (running or suspended) workbenches.",
    example = "analysis"
  )
  val instanceName: String,
  @field:Schema(description = "Compute and storage; defaults apply when omitted.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
  val resources: ResourceSpec? = null,
  @field:Schema(
    description = "Container image (a valid image reference that starts with one of the allowed image prefixes, " +
      "`WORKBENCH_ALLOWED_IMAGE_PREFIXES`). Default `projectcontinuum/continuum-workbench:0.0.5`.",
    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    nullable = true,
    example = "projectcontinuum/continuum-workbench:0.0.14"
  )
  val image: String? = null,
  @field:Schema(
    description = "Overlay variant (see `GET /api/v1/workbench/variants`); null for the base manifests. " +
      "Must match `^[A-Za-z0-9][A-Za-z0-9._-]{0,62}\$` without `--`.",
    requiredMode = Schema.RequiredMode.NOT_REQUIRED,
    nullable = true,
    example = "GPU"
  )
  val variant: String? = null
) {
  fun resolvedResources(): ResourceSpec = resources ?: ResourceSpec()
  fun resolvedImage(): String = image ?: "projectcontinuum/continuum-workbench:0.0.5"
}
