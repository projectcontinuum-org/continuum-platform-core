package org.projectcontinuum.core.cluster.manager.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "continuum.core.cluster-manager.workbench")
data class WorkbenchProperties(
  val defaultImage: String = "projectcontinuum/continuum-workbench:latest",
  val namespace: String = "default",
  val imageRepository: String = "projectcontinuum/continuum-workbench"
)

