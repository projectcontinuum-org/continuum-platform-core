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
import org.projectcontinuum.core.api.server.model.WorkflowRunResponse
import org.projectcontinuum.core.api.server.model.WorkflowRunSummary
import org.projectcontinuum.core.api.server.service.WorkflowRunService
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/workflow-runs", produces = ["application/json"])
@Tag(name = ApiTags.WORKFLOW_RUNS)
@SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
class WorkflowRunController(
  val workflowRunService: WorkflowRunService
) {

  companion object {
    private const val FILTER_DESCRIPTION =
      "RSQL filter over the run fields `workflowId`, `workflowUri`, `status`, `scheduleId`, `progressPercentage`, " +
        "`createdAt` and `updatedAt` (`;` = and, `,` = or; dates ISO-8601). Always narrowed to the caller's own runs."
    private const val FILTER_EXAMPLE = "workflowUri==\"examples/text-cleanup.cwf\";updatedAt>=2026-11-01T00:00:00Z"
  }

  @GetMapping
  @Operation(
    operationId = "listWorkflowRuns",
    summary = "List the caller's workflow runs, newest first",
    description = """
Pages through the caller's runs from the database, newest first, without their snapshot and outputs (use
`GET /api/v1/workflow-runs/{workflowId}` for one run in full). Used by the workbench's run history.

The page is Spring Data's `PagedModel`: `content` plus `page.size`, `page.number`, `page.totalElements` and
`page.totalPages`. Read-only."""
  )
  @ApiResponse(responseCode = "200", description = "One page of run summaries.")
  @ApiResponse(
    responseCode = "400",
    description = "The `filter` is not valid RSQL, names a field that cannot be filtered on, or has a value of the wrong type.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Invalid filter","status":400,"detail":"Invalid filter: it names a field that cannot be filtered on","instance":"/api/v1/workflow-runs"}""")]
      )
    ]
  )
  fun findAll(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = FILTER_DESCRIPTION, example = FILTER_EXAMPLE)
    @RequestParam(required = false) filter: String?,
    @Parameter(description = "Zero-based page number.", example = "0", schema = Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
    @RequestParam(required = false, defaultValue = "0") page: Int,
    @Parameter(description = "Page size (runs per page).", example = "20", schema = Schema(type = "integer", format = "int32", minimum = "1", defaultValue = "20"))
    @RequestParam(required = false, defaultValue = "20") size: Int,
    @Parameter(
      description = "Additional RSQL sort (`field,asc|desc`, `;`-separated) applied on top of the default `createdAt` descending order.",
      example = "status,asc"
    )
    @RequestParam(required = false) sort: String?
  ): Page<WorkflowRunSummary> {
    return workflowRunService.findAll(ownedBy, filter, page, size, sort).map { WorkflowRunSummary.from(it) }
  }

  @GetMapping("/{workflowId}")
  @Operation(
    operationId = "getWorkflowRun",
    summary = "Get one workflow run with its snapshot and node outputs",
    description = """
Returns one of the caller's runs with `data.workflowSnapshot` (the workflow as it ran, with live node and edge statuses)
and `data.nodeToOutputMap` (the outputs produced so far). The workbench's run viewer opens runs with this. The values
are those of the last run event the message bridge stored; live updates stream over MQTT
(`continuum/workflow/execution/{workflowId}/update`). Read-only."""
  )
  @ApiResponse(responseCode = "200", description = "The run.")
  @ApiResponse(
    responseCode = "404",
    description = "No run with this id is owned by the caller (unknown and other tenants' ids are indistinguishable).",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Not Found","status":404,"detail":"Workflow run not found","instance":"/api/v1/workflow-runs/6b1f0e4e-8f4a-4d8c-9d61-3c2b7f1a9e10"}""")]
      )
    ]
  )
  fun findById(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = "Workflow id returned by `POST /api/v1/workflow`.", example = "6b1f0e4e-8f4a-4d8c-9d61-3c2b7f1a9e10")
    @PathVariable workflowId: String
  ): WorkflowRunResponse {
    val entity = workflowRunService.findById(ownedBy, workflowId)
      ?: throw ResourceNotFoundException("Workflow run not found")
    return WorkflowRunResponse.from(entity)
  }

  @GetMapping("/distinct-workflows")
  @Operation(
    operationId = "listRunWorkflowUris",
    summary = "List the distinct workflow files the caller has runs of",
    description = """
Pages through the distinct `workflowUri` values (workflow file paths) of the caller's runs, sorted alphabetically. The
workbench builds the first level of its run history tree from this. With a `filter` the distinct set is computed over
every matching run. Read-only."""
  )
  @ApiResponse(responseCode = "200", description = "One page of workflow URIs.")
  @ApiResponse(
    responseCode = "400",
    description = "The `filter` is not valid RSQL, names a field that cannot be filtered on, or has a value of the wrong type.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Invalid filter","status":400,"detail":"Invalid filter: it names a field that cannot be filtered on","instance":"/api/v1/workflow-runs"}""")]
      )
    ]
  )
  fun findDistinctWorkflows(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = FILTER_DESCRIPTION, example = "updatedAt>=2026-11-01T00:00:00Z")
    @RequestParam(required = false) filter: String?,
    @Parameter(description = "Zero-based page number.", example = "0", schema = Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
    @RequestParam(required = false, defaultValue = "0") page: Int,
    @Parameter(description = "Page size.", example = "50", schema = Schema(type = "integer", format = "int32", minimum = "1", defaultValue = "50"))
    @RequestParam(required = false, defaultValue = "50") size: Int
  ): Page<String> {
    return workflowRunService.findDistinctWorkflows(ownedBy, filter, page, size)
  }
}
