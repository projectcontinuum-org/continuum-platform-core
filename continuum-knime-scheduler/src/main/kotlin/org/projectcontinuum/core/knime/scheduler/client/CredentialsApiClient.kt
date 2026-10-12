package org.projectcontinuum.core.knime.scheduler.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

private const val USER_ID_HEADER = "x-continuum-user-id"

data class CredentialSummaryResponse(val name: String, val type: String)

@Component
class CredentialsApiClient(
  private val apiServerRestTemplate: RestTemplate,
  @Value("\${continuum.core.knime-scheduler.credentials-server-base-url}") private val baseUrl: String
) {

  fun listByType(ownedBy: String, type: String): List<CredentialSummaryResponse> {
    val headers = HttpHeaders().apply { set(USER_ID_HEADER, ownedBy) }
    val response = apiServerRestTemplate.exchange(
      "$baseUrl/api/v1/credentials/type/$type",
      HttpMethod.GET,
      HttpEntity<Void>(headers),
      Array<CredentialSummaryResponse>::class.java
    )
    return response.body?.toList() ?: emptyList()
  }
}
