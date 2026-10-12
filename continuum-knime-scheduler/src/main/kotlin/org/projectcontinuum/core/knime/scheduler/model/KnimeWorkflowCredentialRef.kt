package org.projectcontinuum.core.knime.scheduler.model

data class KnimeWorkflowCredentialRef(
  val knimeCredentialName: String,
  val credential: String
)
