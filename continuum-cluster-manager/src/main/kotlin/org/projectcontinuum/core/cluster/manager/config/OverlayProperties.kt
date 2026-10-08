package org.projectcontinuum.core.cluster.manager.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "continuum.core.cluster-manager.workbench.overlays")
data class OverlayProperties(
  val enabled: Boolean = false,
  val path: String = "/etc/continuum/overlays/workbench"
)
