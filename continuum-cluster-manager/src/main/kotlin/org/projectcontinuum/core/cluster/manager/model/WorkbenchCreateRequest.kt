package org.projectcontinuum.core.cluster.manager.model

data class WorkbenchCreateRequest(
  val instanceName: String,
  val resources: ResourceSpec = ResourceSpec(),
  val image: String = "projectcontinuum/continuum-workbench:0.0.5"
)
