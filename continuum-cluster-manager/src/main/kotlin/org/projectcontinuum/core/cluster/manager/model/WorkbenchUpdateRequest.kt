package org.projectcontinuum.core.cluster.manager.model

data class WorkbenchUpdateRequest(
  val resources: ResourceSpec? = null,
  val image: String? = null
)
