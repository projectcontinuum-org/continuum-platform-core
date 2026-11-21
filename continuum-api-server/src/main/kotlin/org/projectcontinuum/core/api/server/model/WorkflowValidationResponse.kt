package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema
import org.projectcontinuum.core.commons.validation.WorkflowIssue

/**
 * Result of `POST /api/v1/workflow/validate`. An invalid workflow is a normal result (`valid = false`), not an HTTP
 * error, so a client can tell it apart from a malformed request. [errors] would block a start, [warnings] would not.
 */
@Schema(name = "WorkflowValidationResponse", description = "Verdict of the start checks on a workflow.")
data class WorkflowValidationResponse(
  @field:Schema(description = "True when a start would be accepted (no errors).", example = "false")
  val valid: Boolean,
  @field:Schema(description = "Number of nodes in the workflow.", example = "2")
  val nodeCount: Int,
  @field:Schema(description = "Number of edges in the workflow.", example = "1")
  val edgeCount: Int,
  @field:Schema(description = "Problems that would reject a start.")
  val errors: List<WorkflowIssue>,
  @field:Schema(description = "Findings that would not block a start.")
  val warnings: List<WorkflowIssue>
)
