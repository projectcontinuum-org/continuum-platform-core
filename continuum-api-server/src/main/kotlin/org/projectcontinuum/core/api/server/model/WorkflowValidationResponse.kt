package org.projectcontinuum.core.api.server.model

import org.projectcontinuum.core.commons.validation.WorkflowIssue

/**
 * Result of `POST /api/v1/workflow/validate`. An invalid workflow is a normal result (`valid = false`), not an HTTP
 * error, so a client can tell it apart from a malformed request. [errors] would block a start, [warnings] would not.
 */
data class WorkflowValidationResponse(
  val valid: Boolean,
  val nodeCount: Int,
  val edgeCount: Int,
  val errors: List<WorkflowIssue>,
  val warnings: List<WorkflowIssue>
)
