package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema
import org.projectcontinuum.core.commons.validation.WorkflowIssue

/** [warnings] are validation findings that did not block the start, e.g. an input port with no incoming edge. */
@Schema(name = "StartWorkflowResponse", description = "The started run.")
data class StartWorkflowResponse(
  @field:Schema(
    description = "Workflow id of the new run (a UUID). Use it with the status, cancel and terminate endpoints, " +
      "`GET /api/v1/workflow-runs/{workflowId}` and the MQTT topic `continuum/workflow/execution/{workflowId}/update`.",
    example = "6b1f0e4e-8f4a-4d8c-9d61-3c2b7f1a9e10"
  )
  val workflowId: String,
  @field:Schema(description = "Validation findings that did not block the start, e.g. an input port with no incoming edge.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  val warnings: List<WorkflowIssue> = emptyList()
)
