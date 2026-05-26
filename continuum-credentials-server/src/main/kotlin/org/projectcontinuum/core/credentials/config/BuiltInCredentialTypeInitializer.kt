package org.projectcontinuum.core.credentials.config

import org.projectcontinuum.core.credentials.model.CredentialTypeCreateRequest
import org.projectcontinuum.core.credentials.service.CredentialTypeService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Seeds built-in credential types on application startup.
 */
@Component
class BuiltInCredentialTypeInitializer(
  private val credentialTypeService: CredentialTypeService
) : ApplicationRunner {

  private val logger = LoggerFactory.getLogger(BuiltInCredentialTypeInitializer::class.java)

  companion object {
    val BUILT_IN_TYPES: List<CredentialTypeCreateRequest> = listOf(
      CredentialTypeCreateRequest(
        type = "GENERIC",
        schema = mapOf(
          "type" to "object",
          "title" to "Generic Credentials",
          "description" to "A generic set of key-value string pairs",
          "additionalProperties" to mapOf(
            "type" to "string"
          )
        ),
        uiSchema = emptyMap(),
        version = "1.0.0"
      )
    )
  }

  override fun run(args: ApplicationArguments?) {
    for (request in BUILT_IN_TYPES) {
      try {
        credentialTypeService.createType(request)
        logger.info("Registered built-in credential type '{}' v{}", request.type, request.version)
      } catch (e: Exception) {
        logger.debug(
          "Built-in credential type '{}' v{} already exists, skipping",
          request.type, request.version
        )
      }
    }
  }
}

