package org.projectcontinuum.core.cluster.manager.model

data class WorkbenchCreateRequest(
  val instanceName: String,
  val resources: ResourceSpec? = null,
  val image: String? = null
) {
  fun resolvedResources(): ResourceSpec = resources ?: ResourceSpec()
  fun resolvedImage(): String = image ?: "projectcontinuum/continuum-workbench:0.0.5"
}
