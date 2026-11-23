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
import org.projectcontinuum.core.api.server.model.StartWorkflowResponse
import org.projectcontinuum.core.api.server.model.WorkflowValidationResponse
import org.projectcontinuum.core.api.server.service.WorkflowService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@RestController
@RequestMapping("/api/v1/workflow", produces = ["application/json"])
@Tag(name = ApiTags.WORKFLOWS)
@SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
class WorkflowController(
  val workflowService: WorkflowService
) {

  companion object {
    private const val WORKFLOW_ID_DESCRIPTION = "Workflow id returned by `POST /api/v1/workflow`."
  }

  @PostMapping(consumes = ["application/json"])
  @Operation(
    operationId = "startWorkflow",
    summary = "Start a workflow run",
    description = """
Starts one run of a complete workflow (the content of a `.cwf` file), owned by the caller. The workbench calls this
when the user runs the open workflow.

The workflow is first checked exactly like `POST /api/v1/workflow/validate`: unique node ids, every `data.nodeModel`
registered, edge endpoints and ports, no self or duplicate edges, one edge per input port, no cycles, and every node's
`properties` against the **registered** properties schema (the `propertiesSchema`, `inputs` and `outputs` in the body
are ignored), and the workflow `name` must be a valid URI path (it becomes the run's `workflowUri`). An invalid workflow is rejected with every problem listed, and nothing is stored or started. A node type
whose worker has not registered yet is an error: retry once the worker is up.

On success the run is recorded (`PENDING`) and a Temporal workflow is started; the response returns its workflow id.
Status then streams over MQTT (`continuum/workflow/execution/{workflowId}/update`) and is mirrored into
`GET /api/v1/workflow-runs/{workflowId}`. Not idempotent: every call starts a new run."""
  )
  @OpenApiRequestBody(
    description = "The workflow to run.",
    required = true,
    content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "twoNodes", summary = "Create Table → Text Normalizer", value = ApiExamples.TWO_NODE_WORKFLOW)])]
  )
  @ApiResponse(
    responseCode = "200",
    description = "Run started. `warnings` lists findings that did not block it.",
    content = [Content(mediaType = "application/json", schema = Schema(implementation = StartWorkflowResponse::class), examples = [ExampleObject(value = ApiExamples.START_RESPONSE)])]
  )
  @ApiResponse(
    responseCode = "400",
    description = "The workflow is invalid (`errors` lists every problem), or the body is malformed.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.WORKFLOW_VALIDATION_PROBLEM_SCHEMA), examples = [ExampleObject(value = ApiExamples.INVALID_WORKFLOW_PROBLEM)])]
  )
  fun startWorkflow(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @RequestBody
    continuumWorkflowModel: ContinuumWorkflowModel
  ): StartWorkflowResponse {
    return workflowService.startWorkflow(continuumWorkflowModel, ownedBy)
  }

  /**
   * Validates a workflow with the same checks as [startWorkflow] and nothing else: no run row and no Temporal call.
   * Always 200 for a well-formed body; `valid` and `errors` carry the verdict.
   */
  @PostMapping("/validate", consumes = ["application/json"])
  @Operation(
    operationId = "validateWorkflow",
    summary = "Check a workflow without running it",
    description = """
Runs exactly the checks of `POST /api/v1/workflow` against the live node registry and returns the verdict, without
storing or starting anything. Meant for editors and AI agents that want every error and warning while the workflow is
still being written.

An invalid workflow is a normal **200** with `valid: false`, so it can be told apart from a malformed body (400).
Read-only and idempotent (the result follows the registry, which changes when workers register)."""
  )
  @OpenApiRequestBody(
    description = "The workflow to check.",
    required = true,
    content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "twoNodes", summary = "Create Table → Text Normalizer", value = ApiExamples.TWO_NODE_WORKFLOW)])]
  )
  @ApiResponse(
    responseCode = "200",
    description = "The verdict.",
    content = [Content(mediaType = "application/json", schema = Schema(implementation = WorkflowValidationResponse::class), examples = [ExampleObject(name = "invalid", value = ApiExamples.VALIDATION_RESPONSE_INVALID)])]
  )
  fun validateWorkflow(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @RequestBody
    continuumWorkflowModel: ContinuumWorkflowModel
  ): WorkflowValidationResponse {
    return workflowService.validateWorkflow(workflow = continuumWorkflowModel, ownedBy = ownedBy)
  }

  @PostMapping("/{workflowId}/cancel")
  @Operation(
    operationId = "cancelWorkflow",
    summary = "Cancel a running workflow",
    description = """
Asks one of the caller's runs to stop gracefully. The run is signalled at once (running nodes are told to stop and the
run reports `CANCELLED`), and 5 seconds later Temporal cancellation is requested. Returns as soon as the signal was
sent. Repeating it on a running workflow signals again; on a finished one it is a 404."""
  )
  @ApiResponse(responseCode = "204", description = "Cancellation requested.")
  @ApiResponse(
    responseCode = "404",
    description = "No running workflow with this id is owned by the caller (unknown, another tenant's, or already finished).",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.WORKFLOW_NOT_FOUND_PROBLEM)])]
  )
  fun cancelWorkflow(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = WORKFLOW_ID_DESCRIPTION, example = ApiExamples.WORKFLOW_ID)
    @PathVariable workflowId: String,
    @Parameter(description = "Why the run is cancelled; passed to the run's cancellation signal.", example = "Wrong input file")
    @RequestParam(required = false) reason: String?
  ): ResponseEntity<Void> {
    workflowService.cancelWorkflow(ownedBy, workflowId, reason)
    return ResponseEntity.noContent().build()
  }

  @PostMapping("/{workflowId}/terminate")
  @Operation(
    operationId = "terminateWorkflow",
    summary = "Terminate a running workflow immediately",
    description = """
Stops one of the caller's runs without waiting for its nodes. The run is signalled at once (it reports `TERMINATED`),
and 5 seconds later Temporal terminates it with `reason` (default "Terminated via API"); no code of the run executes
after that. Prefer `cancel` unless the run is stuck. On a finished run it is a 404."""
  )
  @ApiResponse(responseCode = "204", description = "Termination requested.")
  @ApiResponse(
    responseCode = "404",
    description = "No running workflow with this id is owned by the caller (unknown, another tenant's, or already finished).",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.WORKFLOW_NOT_FOUND_PROBLEM)])]
  )
  fun terminateWorkflow(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = WORKFLOW_ID_DESCRIPTION, example = ApiExamples.WORKFLOW_ID)
    @PathVariable workflowId: String,
    @Parameter(description = "Why the run is terminated; recorded by Temporal.", example = "Stuck in an external call")
    @RequestParam(required = false) reason: String?
  ): ResponseEntity<Void> {
    workflowService.terminateWorkflow(ownedBy, workflowId, reason)
    return ResponseEntity.noContent().build()
  }
}
