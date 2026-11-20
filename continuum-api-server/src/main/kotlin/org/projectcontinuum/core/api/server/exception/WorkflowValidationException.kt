package org.projectcontinuum.core.api.server.exception

import org.projectcontinuum.core.commons.validation.WorkflowIssue

/**
 * A workflow submitted to run failed validation. Carries every [errors] entry (and the [warnings] found on the way) so
 * [ApiExceptionHandler] can return them all in one 400 `ProblemDetail`.
 */
class WorkflowValidationException(
  val errors: List<WorkflowIssue>,
  val warnings: List<WorkflowIssue>
) : InvalidRequestException(
  "Invalid workflow: ${errors.size} error(s). " + errors.joinToString(" ") { it.message }
)
