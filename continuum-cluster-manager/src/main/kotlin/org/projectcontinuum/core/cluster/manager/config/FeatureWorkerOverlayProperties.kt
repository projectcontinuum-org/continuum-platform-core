package org.projectcontinuum.core.cluster.manager.config

import org.projectcontinuum.core.cluster.manager.service.OverlayDomainProperties
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "continuum.core.cluster-manager.feature-worker.overlays")
data class FeatureWorkerOverlayProperties(
  override val enabled: Boolean = false,
  override val path: String = "/etc/continuum/overlays/feature-worker"
) : OverlayDomainProperties
