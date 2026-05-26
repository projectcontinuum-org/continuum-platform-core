package org.projectcontinuum.core.worker.starter.resolver

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

/**
 * Resolves credentials for a node before its execute() method is called.
 *
 * This class performs two tasks:
 * 1. **Scans** the node's UI Schema to find fields marked with `options.format = "credentials"`
 * 2. **Fetches** the actual credential data from the Credentials Server via HTTP
 *
 * The resolved credentials are returned as a map of `label -> credential data`, where:
 * - The label is `options.credentialLabel` from the UI Schema (or the property name as fallback)
 * - The credential data is the key-value map stored in the credential (e.g., accessKeyId, secretAccessKey)
 *
 * ## UI Schema Example
 * ```json
 * {
 *   "type": "Control",
 *   "scope": "#/properties/awsCredential",
 *   "options": {
 *     "format": "credentials",
 *     "credentialLabel": "AWS Credentials"
 *   }
 * }
 * ```
 *
 * With node properties: `{ "awsCredential": "my-aws-credential-name" }`
 *
 * This will fetch the credential named "my-aws-credential-name" from the credentials server
 * and store it under the key "AWS Credentials" in the result map.
 */
@Component
class CredentialResolver(
  @Qualifier("continuumWorkerSpringbootStarterRestTemplate")
  private val restTemplate: RestTemplate,
  @Value("\${continuum.core.worker.credentials-server-base-url:http://localhost:8083}")
  private val credentialsServerBaseUrl: String
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(CredentialResolver::class.java)
    private val objectMapper = ObjectMapper()
  }

  /**
   * Describes a credential field found in the UI Schema.
   *
   * @param propertyName The property key in the node's properties map (e.g., "awsCredential")
   * @param label The display label / map key (from credentialLabel or propertyName as fallback)
   */
  data class CredentialFieldDescriptor(
    val propertyName: String,
    val label: String
  )

  /**
   * Resolves all credential fields for a node.
   *
   * @param properties The node's configuration properties (contains credential names as values)
   * @param propertiesUISchema The node's UI Schema (contains credential field definitions)
   * @param ownerId The user ID for authentication with the credentials server
   * @return Map of label -> credential data. Empty map if no credential fields are found.
   */
  fun resolve(
    properties: Map<String, Any>,
    propertiesUISchema: Map<String, Any>,
    ownerId: String
  ): Map<String, Map<String, Any>> {
    // Step 1: Find all credential fields in the UI Schema
    val credentialFields = findCredentialFields(propertiesUISchema)

    if (credentialFields.isEmpty()) {
      return emptyMap()
    }

    LOGGER.info("Found ${credentialFields.size} credential field(s) to resolve: ${credentialFields.map { it.label }}")

    // Step 2: Fetch each credential from the credentials server
    val resolvedMap = mutableMapOf<String, Map<String, Any>>()

    for (field in credentialFields) {
      // Read the credential name from the node's properties
      val credentialName = properties[field.propertyName]?.toString()
      if (credentialName.isNullOrBlank()) {
        LOGGER.warn("Credential field '${field.propertyName}' has no value in node properties, skipping")
        continue
      }

      // Fetch the credential data from the credentials server
      val credentialData = fetchCredential(credentialName, ownerId)
      if (credentialData != null) {
        resolvedMap[field.label] = credentialData
        LOGGER.info("Resolved credential '${field.label}' (name=$credentialName)")
      } else {
        LOGGER.warn("Failed to fetch credential '${field.label}' (name=$credentialName)")
      }
    }

    return resolvedMap
  }

  /**
   * Recursively scans the UI Schema to find all controls with `options.format == "credentials"`.
   *
   * Handles nested layouts (VerticalLayout, HorizontalLayout, Group, etc.) that contain
   * an `elements` array of child elements.
   *
   * @param uiSchema The UI Schema (or a sub-element of it) to scan
   * @return List of credential field descriptors found
   */
  fun findCredentialFields(uiSchema: Map<String, Any>): List<CredentialFieldDescriptor> {
    val results = mutableListOf<CredentialFieldDescriptor>()

    // Check if this element is a credential control
    val options = uiSchema["options"] as? Map<*, *>
    if (options != null && options["format"] == "credential") {
      // Extract the property name from the scope (e.g., "#/properties/awsCredential" -> "awsCredential")
      val scope = uiSchema["scope"] as? String
      val propertyName = scope?.substringAfterLast("/")

      if (!propertyName.isNullOrBlank()) {
        // Use credentialLabel if provided, otherwise fall back to the property name
        val label = options["credentialLabel"]?.toString() ?: propertyName
        results.add(CredentialFieldDescriptor(propertyName, label))
      }
    }

    // Recursively scan child elements (for layouts like VerticalLayout, HorizontalLayout, Group, etc.)
    val elements = uiSchema["elements"] as? List<*>
    if (elements != null) {
      for (element in elements) {
        @Suppress("UNCHECKED_CAST")
        val elementMap = element as? Map<String, Any> ?: continue
        results.addAll(findCredentialFields(elementMap))
      }
    }

    return results
  }

  /**
   * Fetches a single credential from the Credentials Server by name.
   *
   * Calls `GET {credentialsServerBaseUrl}/api/v1/credentials/{name}` with
   * the `x-continuum-user-id` header set to the owner ID.
   *
   * Note: RestTemplate throws on non-2xx responses by default, so we only
   * need to catch exceptions — no manual status code checking is needed.
   *
   * @param name The credential name to fetch
   * @param ownerId The user ID for authentication
   * @return The credential's data map (key-value pairs), or null if the fetch fails
   */
  private fun fetchCredential(name: String, ownerId: String): Map<String, Any>? {
    return try {
      val url = "$credentialsServerBaseUrl/api/v1/credentials/$name"

      val headers = HttpHeaders()
      headers.set("x-continuum-user-id", ownerId)

      // RestTemplate throws HttpClientErrorException / HttpServerErrorException for non-2xx,
      // so a successful exchange() always means 2xx status.
      val response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        HttpEntity<Void>(headers),
        String::class.java
      )

      // Parse the response body and extract the "data" field containing credential key-value pairs
      val body = response.body
      if (body != null) {
        val responseMap: Map<String, Any> = objectMapper.readValue(
          body,
          object : TypeReference<Map<String, Any>>() {}
        )
        @Suppress("UNCHECKED_CAST")
        responseMap["data"] as? Map<String, Any>
      } else {
        LOGGER.warn("Server returned empty body for credential '$name'")
        null
      }
    } catch (e: Exception) {
      LOGGER.error("Error fetching credential '$name' from server: ${e.message}", e)
      null
    }
  }
}
