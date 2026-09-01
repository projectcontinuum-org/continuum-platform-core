package org.projectcontinuum.core.knime.scheduler.model

data class KnimeWorkflowVariable(
  val name: String,
  val value: String,
  val type: String = "String"
)
