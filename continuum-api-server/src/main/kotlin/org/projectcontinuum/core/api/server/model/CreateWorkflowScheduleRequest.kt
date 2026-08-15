package org.projectcontinuum.core.api.server.model

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

data class CreateWorkflowScheduleRequest(
  val name: String,
  val cronExpression: String,
  val timeZone: String? = null,
  val continuumWorkflowModel: ContinuumWorkflowModel
)
