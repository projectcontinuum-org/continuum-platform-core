package org.projectcontinuum.core.api.server.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.projectcontinuum.core.api.server.config.ApiTags
import org.projectcontinuum.core.api.server.config.OpenApiConfig
import org.projectcontinuum.core.api.server.exception.ResourceNotFoundException
import org.projectcontinuum.core.api.server.model.ScheduleValidationReport
import org.projectcontinuum.core.api.server.service.ScheduleValidationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Operator endpoints. They act across all tenants, so they are not owner-scoped like the rest of the API. Instead
 * the caller must be in an admin group: one of the comma-separated groups in `x-continuum-user-groups` must be in
 * `continuum.core.api-server.admin.groups` (env `CONTINUUM_ADMIN_GROUPS`, also comma-separated).
 *
 * The groups header is trusted exactly like `x-continuum-user-id`: the ingress / auth layer must set it from the
 * authenticated identity and strip any value sent by the client, or anyone can claim a group.
 *
 * With no admin groups configured (the default) every admin endpoint is a 404, exactly as if it did not exist, and so
 * is a caller in no admin group: an admin endpoint cannot be found by probing.
 */
@RestController
@RequestMapping("/api/v1/admin", produces = ["application/json"])
@Tag(name = ApiTags.ADMINISTRATION)
@SecurityRequirement(name = OpenApiConfig.USER_GROUPS_SECURITY_SCHEME)
class AdminController(
  private val scheduleValidationService: ScheduleValidationService,
  @Value("\${continuum.core.api-server.admin.groups:}")
  adminGroups: String
) {

  companion object {
    const val USER_GROUPS_HEADER = "x-continuum-user-groups"

    /** Trimmed, non-blank entries of a comma-separated list. Group names are compared exactly (case-sensitive). */
    fun parseGroups(csv: String?): Set<String> =
      csv.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
  }

  private val adminGroups: Set<String> = parseGroups(adminGroups)

  /**
   * Checks every stored schedule against the current node registry and returns the ones that will fail on the
   * worker. Read-only. Run it after upgrading, or after a node's schema changes.
   */
  @PostMapping("/schedules/validate")
  @Operation(
    operationId = "validateStoredSchedules",
    summary = "Check every stored schedule against the node registry",
    description = """
Checks the workflow of every stored schedule (all tenants) against the current node registry and reports the ones
whose runs will fail on the worker: invalid node properties, or node types that are not registered. Run it after an
upgrade or after a node's schema changed. Report only: nothing is paused, changed or deleted.

Internal: only in the `internal` spec group. The caller must be in an admin group (`x-continuum-user-groups`, matched
against `CONTINUUM_ADMIN_GROUPS`); otherwise, and whenever no admin group is configured, the endpoint answers 404 as
if it did not exist."""
  )
  @ApiResponse(responseCode = "200", description = "The report.")
  @ApiResponse(
    responseCode = "404",
    description = "Admin endpoints are disabled, or the caller is in no admin group.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Not Found","status":404,"detail":"Not found","instance":"/api/v1/admin/schedules/validate"}""")]
      )
    ]
  )
  fun validateSchedules(
    @Parameter(hidden = true)
    @RequestHeader(USER_GROUPS_HEADER, required = false) userGroups: String?
  ): ScheduleValidationReport {
    requireAdmin(userGroups)
    return scheduleValidationService.validateAll()
  }

  private fun requireAdmin(userGroups: String?) {
    if (adminGroups.isEmpty() || parseGroups(userGroups).none { it in adminGroups }) {
      throw ResourceNotFoundException("Not found")
    }
  }
}
