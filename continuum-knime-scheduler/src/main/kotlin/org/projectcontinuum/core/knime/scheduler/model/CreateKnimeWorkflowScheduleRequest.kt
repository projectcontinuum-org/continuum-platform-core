package org.projectcontinuum.core.knime.scheduler.model

import java.util.UUID

data class CreateKnimeWorkflowScheduleRequest(
  val name: String,
  val cronExpression: String,
  val timeZone: String? = null,
  val knimeWorkflowId: UUID,
  val resetWorkflow: Boolean = false,
  val timeoutSeconds: Long = 300
)
