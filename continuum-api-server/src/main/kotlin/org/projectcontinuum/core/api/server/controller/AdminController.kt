package org.projectcontinuum.core.api.server.controller

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.projectcontinuum.core.api.server.config.ApiTags
import org.projectcontinuum.core.api.server.config.OpenApiConfig
import org.projectcontinuum.core.api.server.model.ScheduleValidationReport
import org.projectcontinuum.core.api.server.service.ScheduleValidationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Operator endpoints. They act across all tenants, so they are not owner-scoped like the rest of the API. Instead
 * the caller must be in an admin group: one of the comma-separated groups in `x-continuum-user-groups` must be in
 * `continuum.core.api-server.admin.groups` (env `CONTINUUM_ADMIN_GROUPS`, also comma-separated).
 *
 * The groups header is trusted exactly like `x-continuum-user-id`: the ingress / auth layer must set it from the
 * authenticated identity and strip any value sent by the client, or anyone can claim a group.
 *
 * With no admin groups configured (the default) every admin endpoint is a 404, exactly as if it did not exist, and so
 * is a caller in no admin group: an admin endpoint cannot be found by probing. "Exactly" means the same response as an
 * unknown path for **any** method and `Accept`, so:
 * - [requireAdmin] throws Spring's own [NoResourceFoundException], whose body is that of an unknown path;
 * - the mappings declare no `produces`/`consumes` (those would answer 406/415 before the admin check runs);
 * - [rejectOtherMethods] catches every other method of each admin path, so a non-admin gets 404 instead of 405.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = ApiTags.ADMINISTRATION)
@SecurityRequirement(name = OpenApiConfig.USER_GROUPS_SECURITY_SCHEME)
class AdminController(
  private val scheduleValidationService: ScheduleValidationService,
  @Value("\${continuum.core.api-server.admin.groups:}")
  adminGroups: String
) {

  companion object {
    const val USER_GROUPS_HEADER = "x-continuum-user-groups"
    private const val VALIDATE_SCHEDULES_PATH = "/schedules/validate"

    /** Trimmed, non-blank entries of a comma-separated list. Group names are compared exactly (case-sensitive). */
    fun parseGroups(csv: String?): Set<String> =
      csv.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
  }

  private val adminGroups: Set<String> = parseGroups(adminGroups)

  /**
   * Checks every stored schedule against the current node registry and returns the ones that will fail on the
   * worker. Read-only. Run it after upgrading, or after a node's schema changes.
   */
  @PostMapping(VALIDATE_SCHEDULES_PATH)
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
  @ApiResponse(
    responseCode = "200",
    description = "The report.",
    content = [Content(mediaType = "application/json", schema = Schema(implementation = ScheduleValidationReport::class))]
  )
  @ApiResponse(
    responseCode = "404",
    description = "Admin endpoints are disabled, or the caller is in no admin group. The same response as an unknown path.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Not Found","status":404,"detail":"No static resource api/v1/admin/schedules/validate.","instance":"/api/v1/admin/schedules/validate"}""")]
      )
    ]
  )
  fun validateSchedules(
    @Parameter(hidden = true)
    @RequestHeader(USER_GROUPS_HEADER, required = false) userGroups: String?,
    request: HttpServletRequest
  ): ScheduleValidationReport {
    requireAdmin(userGroups, request)
    return scheduleValidationService.validateAll()
  }

  /**
   * Every method other than the documented one, on every admin path. A non-admin gets the unknown-path 404 (not the
   * 405 Spring would answer for a known path); an admin gets the real 405.
   */
  @Hidden
  @RequestMapping(VALIDATE_SCHEDULES_PATH)
  fun rejectOtherMethods(
    @RequestHeader(USER_GROUPS_HEADER, required = false) userGroups: String?,
    request: HttpServletRequest
  ): Nothing {
    requireAdmin(userGroups, request)
    throw HttpRequestMethodNotSupportedException(request.method, listOf(HttpMethod.POST.name()))
  }

  private fun requireAdmin(userGroups: String?, request: HttpServletRequest) {
    if (adminGroups.isEmpty() || parseGroups(userGroups).none { it in adminGroups }) {
      throw NoResourceFoundException(
        HttpMethod.valueOf(request.method),
        request.requestURI,
        request.requestURI.removePrefix(request.contextPath).removePrefix("/")
      )
    }
  }
}
