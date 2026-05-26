package org.projectcontinuum.core.credentials.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "continuum.core.credentials")
data class CredentialsProperties(
  val storageBackend: String = "postgres"
)
