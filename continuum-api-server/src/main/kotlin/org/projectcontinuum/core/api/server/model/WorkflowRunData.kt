package org.projectcontinuum.core.api.server.model

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

data class WorkflowRunData(
  val workflowSnapshot: ContinuumWorkflowModel,
  val nodeToOutputMap: Map<String, Any>
)