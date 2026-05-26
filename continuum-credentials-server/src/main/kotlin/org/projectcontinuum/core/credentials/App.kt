package org.projectcontinuum.core.credentials

import org.projectcontinuum.core.credentials.config.CredentialsProperties
import org.projectcontinuum.core.credentials.config.EncryptionProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(CredentialsProperties::class, EncryptionProperties::class)
class App

fun main(args: Array<String>) {
  runApplication<App>(*args)
}
