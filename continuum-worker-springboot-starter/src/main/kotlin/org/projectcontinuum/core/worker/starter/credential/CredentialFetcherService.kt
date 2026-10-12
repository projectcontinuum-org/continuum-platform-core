package org.projectcontinuum.core.worker.starter.credential

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import tools.jackson.core.type.TypeReference
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Fetches a single named credential from the Credentials Server on demand.
 *
 * Calls `GET {credentialsServerBaseUrl}/api/v1/credentials/{name}` with the
 * `x-continuum-user-id` header set to the owner ID, and returns the credential's
 * `data` field (a key-value map, e.g. `login`/`password` or `accessKeyId`/`secretAccessKey`).
 */
@Component
class CredentialFetcherService(
  @param:Qualifier("continuumWorkerSpringbootStarterRestTemplate")
  private val restTemplate: RestTemplate,
  @param:Value("\${continuum.core.worker.credentials-server-base-url:http://localhost:8083}")
  private val credentialsServerBaseUrl: String
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(CredentialFetcherService::class.java)
    private val objectMapper = jacksonObjectMapper()
  }

  /**
   * Fetches a single credential from the Credentials Server by name.
   *
   * Note: RestTemplate throws on non-2xx responses by default, so we only
   * need to catch exceptions — no manual status code checking is needed.
   *
   * @param name The credential name to fetch
   * @param ownerId The user ID for authentication
   * @return The credential's data map (key-value pairs), or null if the fetch fails
   */
  fun fetch(name: String, ownerId: String): Map<String, Any>? {
    return try {
      val url = "$credentialsServerBaseUrl/api/v1/credentials/$name"

      val headers = HttpHeaders()
      headers.set("x-continuum-user-id", ownerId)

      val response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        HttpEntity<Void>(headers),
        String::class.java
      )

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
