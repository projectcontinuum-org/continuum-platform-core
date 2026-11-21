package org.projectcontinuum.core.api.server.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.projectcontinuum.core.api.server.config.ApiTags
import org.projectcontinuum.core.api.server.config.OpenApiConfig
import org.projectcontinuum.core.api.server.exception.WorkflowScheduleNotFoundException
import org.projectcontinuum.core.api.server.model.CreateWorkflowScheduleRequest
import org.projectcontinuum.core.api.server.model.WorkflowScheduleResponse
import org.projectcontinuum.core.api.server.service.WorkflowScheduleService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@RestController
@RequestMapping("/api/v1/workflow/schedule", produces = ["application/json"])
@Tag(name = ApiTags.WORKFLOW_SCHEDULES)
@SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
class WorkflowScheduleController(
  private val workflowScheduleService: WorkflowScheduleService
) {

  companion object {
    private const val SCHEDULE_ID_DESCRIPTION = "Schedule id (UUID) returned when the schedule was created."
  }

  @PostMapping(consumes = ["application/json"])
  @Operation(
    operationId = "createWorkflowSchedule",
    summary = "Create a cron schedule for a workflow",
    description = """
Creates a Temporal schedule that starts the given workflow on every match of `cronExpression` (in `timeZone`, default
UTC), owned by the caller. Used by the workbench's schedule dialog and the KNIME scheduler.

The workflow is validated exactly like `POST /api/v1/workflow` before anything is created. It is stored as sent and is
**not** revalidated when the schedule fires; operators can check stored schedules with the administration endpoint.
Each fire starts a run (workflow id = schedule id) whose `scheduleId` is set in the run history. Not idempotent: every
call creates a new schedule."""
  )
  @OpenApiRequestBody(
    description = "The schedule and the workflow it runs.",
    required = true,
    content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "nightly", summary = "Every night at 02:00 Berlin time", value = ApiExamples.CREATE_SCHEDULE_REQUEST)])]
  )
  @ApiResponse(responseCode = "201", description = "Schedule created.")
  @ApiResponse(
    responseCode = "400",
    description = "The workflow is invalid (`errors` lists every problem), Temporal rejected the cron expression or time zone, or the body is malformed.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.WORKFLOW_VALIDATION_PROBLEM_SCHEMA), examples = [ExampleObject(value = ApiExamples.INVALID_WORKFLOW_PROBLEM)])]
  )
  fun createSchedule(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @RequestBody request: CreateWorkflowScheduleRequest
  ): ResponseEntity<WorkflowScheduleResponse> =
    ResponseEntity.status(HttpStatus.CREATED).body(workflowScheduleService.createSchedule(request, ownedBy))

  @GetMapping
  @Operation(
    operationId = "listWorkflowSchedules",
    summary = "List the caller's schedules",
    description = """
Lists the caller's schedules with their live Temporal state (`paused`, `nextRunTimes`), optionally only those with an
exact `name`. Read-only."""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The schedules.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = WorkflowScheduleResponse::class)))]
  )
  @ApiResponse(
    responseCode = "500",
    description = "A stored schedule no longer exists in Temporal (TODO: one stale schedule fails the whole list), or Temporal failed.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA))]
  )
  fun listSchedules(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = "Only schedules with exactly this name.", example = "examples/text-cleanup.cwf/nightly.schedule")
    @RequestParam(required = false) name: String?
  ): List<WorkflowScheduleResponse> = workflowScheduleService.listSchedules(ownedBy, name)

  @GetMapping("/filter")
  @Operation(
    operationId = "filterWorkflowSchedules",
    summary = "List the caller's schedules matching an RSQL filter",
    description = """
Lists the caller's schedules that match an RSQL filter over `name`, `cronExpression`, `timeZone`, `scheduleId`,
`createdAt` and `updatedAt` (e.g. `name=like=*nightly*`). Always narrowed to the caller's own schedules. Read-only."""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The matching schedules.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = WorkflowScheduleResponse::class)))]
  )
  @ApiResponse(
    responseCode = "400",
    description = "The `filter` is not valid RSQL, names a field that cannot be filtered on, or has a value of the wrong type.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Invalid filter","status":400,"detail":"Invalid filter: it names a field that cannot be filtered on","instance":"/api/v1/workflow/schedule/filter"}""")]
      )
    ]
  )
  fun listSchedulesByRsql(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = "RSQL filter; omit for all of the caller's schedules.", example = "name=like=*nightly*")
    @RequestParam(required = false) filter: String?
  ): List<WorkflowScheduleResponse> = workflowScheduleService.listSchedulesByRsql(ownedBy, filter)

  @GetMapping("/{scheduleId}")
  @Operation(
    operationId = "getWorkflowSchedule",
    summary = "Get one schedule",
    description = "Returns one of the caller's schedules with its live Temporal state. Read-only."
  )
  @ApiResponse(responseCode = "200", description = "The schedule.")
  @ApiResponse(
    responseCode = "404",
    description = "No schedule with this id is owned by the caller.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.SCHEDULE_NOT_FOUND_PROBLEM)])]
  )
  fun getSchedule(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = SCHEDULE_ID_DESCRIPTION, example = ApiExamples.SCHEDULE_ID)
    @PathVariable scheduleId: UUID
  ): WorkflowScheduleResponse =
    workflowScheduleService.getSchedule(ownedBy, scheduleId)
      ?: throw WorkflowScheduleNotFoundException("Schedule $scheduleId not found")

  @PostMapping("/{scheduleId}/pause")
  @Operation(
    operationId = "pauseWorkflowSchedule",
    summary = "Pause a schedule",
    description = "Stops the schedule from firing until it is unpaused. Runs already started continue. Idempotent."
  )
  @ApiResponse(responseCode = "204", description = "Paused.")
  @ApiResponse(
    responseCode = "404",
    description = "No schedule with this id is owned by the caller.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.SCHEDULE_NOT_FOUND_PROBLEM)])]
  )
  fun pauseSchedule(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = SCHEDULE_ID_DESCRIPTION, example = ApiExamples.SCHEDULE_ID)
    @PathVariable scheduleId: UUID,
    @Parameter(description = "Note stored with the pause in Temporal.", example = "Paused for the database migration")
    @RequestParam(required = false) note: String?
  ): ResponseEntity<Void> {
    workflowScheduleService.pauseSchedule(ownedBy, scheduleId, note)
    return ResponseEntity.noContent().build()
  }

  @PostMapping("/{scheduleId}/unpause")
  @Operation(
    operationId = "unpauseWorkflowSchedule",
    summary = "Resume a paused schedule",
    description = "Lets the schedule fire again from its next cron match (missed fires are not caught up). Idempotent."
  )
  @ApiResponse(responseCode = "204", description = "Unpaused.")
  @ApiResponse(
    responseCode = "404",
    description = "No schedule with this id is owned by the caller.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.SCHEDULE_NOT_FOUND_PROBLEM)])]
  )
  fun unpauseSchedule(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = SCHEDULE_ID_DESCRIPTION, example = ApiExamples.SCHEDULE_ID)
    @PathVariable scheduleId: UUID,
    @Parameter(description = "Note stored with the unpause in Temporal.", example = "Migration done")
    @RequestParam(required = false) note: String?
  ): ResponseEntity<Void> {
    workflowScheduleService.unpauseSchedule(ownedBy, scheduleId, note)
    return ResponseEntity.noContent().build()
  }

  @PostMapping("/{scheduleId}/trigger")
  @Operation(
    operationId = "triggerWorkflowSchedule",
    summary = "Run a schedule's workflow now",
    description = """
Starts one run of the schedule's workflow immediately, outside the cron, even while paused. Not idempotent. The run's
workflow id is the schedule id, so Temporal's overlap policy decides what happens while an earlier run of the schedule
is still going (TODO: not handled explicitly)."""
  )
  @ApiResponse(responseCode = "204", description = "Run requested.")
  @ApiResponse(
    responseCode = "404",
    description = "No schedule with this id is owned by the caller.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.SCHEDULE_NOT_FOUND_PROBLEM)])]
  )
  fun triggerNow(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = SCHEDULE_ID_DESCRIPTION, example = ApiExamples.SCHEDULE_ID)
    @PathVariable scheduleId: UUID
  ): ResponseEntity<Void> {
    workflowScheduleService.triggerNow(ownedBy, scheduleId)
    return ResponseEntity.noContent().build()
  }

  @DeleteMapping("/{scheduleId}")
  @Operation(
    operationId = "deleteWorkflowSchedule",
    summary = "Delete a schedule",
    description = """
Deletes the Temporal schedule and its record. Runs it already started continue; their `scheduleId` in the run history
becomes null. A second delete is a 404."""
  )
  @ApiResponse(responseCode = "204", description = "Deleted.")
  @ApiResponse(
    responseCode = "404",
    description = "No schedule with this id is owned by the caller.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.SCHEDULE_NOT_FOUND_PROBLEM)])]
  )
  fun deleteSchedule(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = SCHEDULE_ID_DESCRIPTION, example = ApiExamples.SCHEDULE_ID)
    @PathVariable scheduleId: UUID
  ): ResponseEntity<Void> {
    workflowScheduleService.deleteSchedule(ownedBy, scheduleId)
    return ResponseEntity.noContent().build()
  }
}
