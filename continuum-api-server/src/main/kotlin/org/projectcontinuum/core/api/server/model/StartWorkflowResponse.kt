package org.projectcontinuum.core.api.server.model

import org.projectcontinuum.core.commons.validation.WorkflowIssue

/** [warnings] are validation findings that did not block the start, e.g. an input port with no incoming edge. */
data class StartWorkflowResponse(
  val workflowId: String,
  val warnings: List<WorkflowIssue> = emptyList()
)
