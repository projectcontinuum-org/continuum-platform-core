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
import org.projectcontinuum.core.api.server.model.CountWorkflowResponse
import org.projectcontinuum.core.api.server.model.StartWorkflowResponse
import org.projectcontinuum.core.api.server.model.WorkflowStatus
import org.projectcontinuum.core.api.server.model.WorkflowValidationResponse
import org.projectcontinuum.core.api.server.service.WorkflowService
import org.projectcontinuum.core.api.server.utils.TreeHelper
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
    private const val VISIBILITY_QUERY_DESCRIPTION =
      "Optional Temporal visibility filter (e.g. `ExecutionStatus = 'Running'`). It is AND-combined with the caller's " +
        "own runs, so it can only narrow the result. Rejected (400) with unbalanced parentheses, unterminated quotes, " +
        "`;`, comments (`--`, `/*`, `#`), backslashes or control characters."
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

  @GetMapping("/active")
  @Operation(
    operationId = "listActiveWorkflowIds",
    summary = "List the ids of the caller's running workflows",
    description = "Asks Temporal for the caller's executions that are still running and returns their workflow ids. Read-only."
  )
  @ApiResponse(
    responseCode = "200",
    description = "Workflow ids; empty when nothing runs.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(type = "string", example = ApiExamples.WORKFLOW_ID)))]
  )
  fun getActiveWorkflows(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String
  ): List<String> {
    return workflowService.getActiveWorkflows(ownedBy).map { it.workflowId }
  }

  @GetMapping("/{workflowId}/status")
  @Operation(
    operationId = "getWorkflowExecutionStatus",
    summary = "Get the Temporal status of one workflow run",
    description = """
Returns the Temporal execution status and search attributes of one of the caller's runs. For the run's node statuses
and outputs use `GET /api/v1/workflow-runs/{workflowId}`. Read-only."""
  )
  @ApiResponse(responseCode = "200", description = "The execution status.")
  @ApiResponse(
    responseCode = "404",
    description = "No run with this id is owned by the caller (unknown and other tenants' ids are indistinguishable), " +
      "or Temporal no longer has its execution (retention expired).",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.WORKFLOW_NOT_FOUND_PROBLEM)])]
  )
  fun getWorkflowStatusById(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = WORKFLOW_ID_DESCRIPTION, example = ApiExamples.WORKFLOW_ID)
    @PathVariable
    workflowId: String
  ): WorkflowStatus {
    return workflowService.getWorkflowStatusById(ownedBy, workflowId)
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

  /**
   * `query` is an optional Temporal visibility filter. It is validated (400 if it could escape its
   * parentheses) and AND-combined with the caller's owner filter, so it can only narrow the result.
   */
  @GetMapping("/list")
  @Operation(
    operationId = "listWorkflowExecutions",
    summary = "List the caller's workflow executions from Temporal",
    description = """
Lists every one of the caller's executions that Temporal still knows (all pages), optionally narrowed by a visibility
query. For the run history with snapshots use `GET /api/v1/workflow-runs`. Read-only."""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The executions.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = WorkflowStatus::class)))]
  )
  @ApiResponse(
    responseCode = "400",
    description = "The `query` could escape the caller's filter, or Temporal rejected it (e.g. an unknown search attribute).",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.INVALID_QUERY_PROBLEM)])]
  )
  fun listWorkflow(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = VISIBILITY_QUERY_DESCRIPTION, example = "ExecutionStatus = 'Running'")
    @RequestParam(
      required = false,
      defaultValue = ""
    )
    query: String
  ): List<WorkflowStatus> {
    return workflowService.listAllWorkflow(ownedBy, query)
  }

  @GetMapping("/count")
  @Operation(
    operationId = "countWorkflowExecutions",
    summary = "Count the caller's workflow executions in Temporal",
    description = """
Counts the caller's Temporal executions, optionally narrowed by a visibility query. A trailing
`GROUP BY ExecutionStatus` also returns one count per status in `groups`. Read-only."""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The count.",
    content = [Content(mediaType = "application/json", schema = Schema(implementation = CountWorkflowResponse::class), examples = [ExampleObject(value = """{"count": 12, "groups": [{"name": "\"Completed\"", "count": 9}, {"name": "\"Running\"", "count": 3}]}""")])]
  )
  @ApiResponse(
    responseCode = "400",
    description = "The `query` could escape the caller's filter, or Temporal rejected it (e.g. an unknown search attribute).",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.INVALID_QUERY_PROBLEM)])]
  )
  fun countWorkflow(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = "$VISIBILITY_QUERY_DESCRIPTION May end with `GROUP BY ExecutionStatus`.", example = "WorkflowType = 'IContinuumWorkflow' GROUP BY ExecutionStatus")
    @RequestParam(
      required = false,
      defaultValue = ""
    )
    query: String
  ): CountWorkflowResponse {
    val response = workflowService.countWorkflow(ownedBy, query)
    return CountWorkflowResponse(
      count = response.count.toInt(),
      groups = response.groupsList.map {
        CountWorkflowResponse.Group(
          name = it.groupValuesList.first().data.toStringUtf8(),
          count = it.count.toInt()
        )
      }
    )
  }

  @GetMapping("/tree")
  @Operation(
    operationId = "getWorkflowExecutionTree",
    summary = "Get the caller's executions grouped by workflow path",
    description = """
Builds a folder tree from the workflow names (paths) of the caller's executions below `baseDir`; each leaf is one
execution with its submitted workflow and its outputs, rebuilt from the Temporal history. Newest first, at most 100
per folder. Expensive: it reads the full history of every listed execution. Read-only."""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The tree roots.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = TreeHelper.WorkflowTreeItem::class)))]
  )
  @ApiResponse(
    responseCode = "400",
    description = "`baseDir` is missing, or the `query` could escape the caller's filter or was rejected by Temporal.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = ApiExamples.INVALID_QUERY_PROBLEM)])]
  )
  fun getWorkflowTree(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(description = "Path prefix removed from every workflow name before it is split into folders.", example = "/home/continuum/workspace", required = true)
    @RequestParam
    baseDir: String,
    @Parameter(description = VISIBILITY_QUERY_DESCRIPTION, example = "ExecutionStatus = 'Completed'")
    @RequestParam(
      required = false,
      defaultValue = ""
    )
    query: String
  ): List<TreeHelper.TreeItem<TreeHelper.Execution>> {
    return workflowService.getWorkflowTree(ownedBy, baseDir, query)
  }

}
