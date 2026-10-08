package org.projectcontinuum.core.cluster.manager.model

data class WorkbenchLivenessResponse(
  val ready: Boolean,
  val status: String
)
