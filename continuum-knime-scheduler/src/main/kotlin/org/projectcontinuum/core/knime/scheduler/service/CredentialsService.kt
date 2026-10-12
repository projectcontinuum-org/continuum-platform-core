package org.projectcontinuum.core.knime.scheduler.service

import org.projectcontinuum.core.knime.scheduler.client.CredentialSummaryResponse
import org.projectcontinuum.core.knime.scheduler.client.CredentialsApiClient
import org.springframework.stereotype.Service

@Service
class CredentialsService(
  private val credentialsApiClient: CredentialsApiClient
) {

  fun listByType(ownedBy: String, type: String): List<CredentialSummaryResponse> =
    credentialsApiClient.listByType(ownedBy, type)
}
