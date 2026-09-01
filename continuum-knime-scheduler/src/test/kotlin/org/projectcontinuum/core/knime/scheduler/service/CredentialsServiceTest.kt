package org.projectcontinuum.core.knime.scheduler.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.knime.scheduler.client.CredentialSummaryResponse
import org.projectcontinuum.core.knime.scheduler.client.CredentialsApiClient

class CredentialsServiceTest {

  private val credentialsApiClient: CredentialsApiClient = mock()
  private val service = CredentialsService(credentialsApiClient)

  @Test
  fun `listByType delegates to the API client and returns its result`() {
    val credentials = listOf(CredentialSummaryResponse(name = "db-cred", type = "GENERIC"))
    whenever(credentialsApiClient.listByType("alice", "GENERIC")).thenReturn(credentials)

    val result = service.listByType("alice", "GENERIC")

    assertEquals(credentials, result)
  }
}
