package org.projectcontinuum.core.credentials.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "continuum.core.credentials.encryption")
data class EncryptionProperties(
  val masterKey: String = ""
)
