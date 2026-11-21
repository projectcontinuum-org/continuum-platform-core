package org.projectcontinuum.core.cluster.manager.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "WorkbenchLiveness", description = "Whether the workbench can be opened.")
data class WorkbenchLivenessResponse(
  @field:Schema(description = "True when the workbench is RUNNING and its pod reports ready.", example = "true")
  val ready: Boolean,
  @field:Schema(
    description = "Stored workbench status.",
    allowableValues = ["PENDING", "RUNNING", "FAILED", "SUSPENDED", "UNKNOWN", "TERMINATING", "DELETED"],
    example = "RUNNING"
  )
  val status: String
)
