package org.projectcontinuum.core.knime.scheduler.controller

import org.projectcontinuum.core.knime.scheduler.client.CredentialSummaryResponse
import org.projectcontinuum.core.knime.scheduler.service.CredentialsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val USER_ID_HEADER = "x-continuum-user-id"

// Workflow-credential rows (see KnimeScheduleWorkflowMapper) only ever reference GENERIC
// credentials today. If that ever changes, update this constant - the frontend has no say
// in the type and doesn't need to change.
private const val WORKFLOW_CREDENTIAL_TYPE = "GENERIC"

@RestController
@RequestMapping("/api/v1/credentials")
class CredentialsController(
  private val credentialsService: CredentialsService
) {

  @GetMapping
  fun list(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String
  ): List<CredentialSummaryResponse> = credentialsService.listByType(userId, WORKFLOW_CREDENTIAL_TYPE)
}

