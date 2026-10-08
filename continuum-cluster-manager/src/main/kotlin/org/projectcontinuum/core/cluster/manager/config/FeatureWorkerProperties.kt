package org.projectcontinuum.core.cluster.manager.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "continuum.core.cluster-manager.feature-worker")
data class FeatureWorkerProperties(
  val namespace: String = "default",
  val imagePullPolicy: String = "IfNotPresent"
)
