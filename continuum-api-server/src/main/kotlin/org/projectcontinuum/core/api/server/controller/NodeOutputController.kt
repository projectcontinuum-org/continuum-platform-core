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
import org.projectcontinuum.core.api.server.model.CellDto
import org.projectcontinuum.core.api.server.model.NodeOutputPage
import org.projectcontinuum.core.api.server.model.Page
import org.projectcontinuum.core.api.server.repository.NodeOutputRepository
import org.projectcontinuum.core.api.server.service.NodeOutputService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/data", produces = ["application/json"])
@Tag(name = ApiTags.NODE_OUTPUTS)
@SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
class NodeOutputController(
  val nodeOutputService: NodeOutputService
) {

  companion object {
    private const val ID_RULE = "Must match `^[A-Za-z0-9._-]{1,255}\$`, must not be `.` and must not contain `..`."
  }

  /**
   * `runId` is the Temporal run id: the first segment of a stored output path (`{remote}<runId>/<nodeId>/...`),
   * which is what the workbench sends. It is not the workflow id returned by `POST /api/v1/workflow`.
   */
  @GetMapping("{runId}/nodes/{nodeId}/outputs/{outputId}")
  @Operation(
    operationId = "getNodeOutputPage",
    summary = "Read one page of a node output table",
    description = """
Returns one page of rows of a table a node produced, for previews in the workbench. The ids come from the output's
location in the run (`{remote}<runId>/<nodeId>/output.<outputId>.parquet`, see `data.nodeToOutputMap` of
`GET /api/v1/workflow-runs/{workflowId}`).

The caller must own the run. On first access the Parquet file is downloaded from object storage into the server's
cache; the page is then read from it. Read-only."""
  )
  @ApiResponse(
    responseCode = "200",
    description = "One page of rows.",
    content = [
      Content(
        mediaType = "application/json",
        schema = Schema(implementation = NodeOutputPage::class),
        examples = [ExampleObject(value = """{"data":[[{"name":"clean_name","value":"YWxpY2U=","contentType":"text/plain"}],[{"name":"clean_name","value":"Ym9i","contentType":"text/plain"}]],"currentPage":0,"currentPageSize":10,"totalPages":1,"totalElements":2,"hasNext":false,"hasPrevious":false}""")]
      )
    ]
  )
  @ApiResponse(
    responseCode = "400",
    description = "An id is not allowed, `page` is negative or `pageSize` is outside 1..1000.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Bad Request","status":400,"detail":"Invalid pageSize: must be between 1 and 1000","instance":"/api/v1/data/0b7c6f0a-2a55-4f43-9a36-0c8f4d2b1e77/nodes/normalize/outputs/data"}""")]
      )
    ]
  )
  @ApiResponse(
    responseCode = "404",
    description = "No run with this run id is owned by the caller (unknown and other tenants' runs are indistinguishable), " +
      "or the run has no stored output for this node and port.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Not Found","status":404,"detail":"Workflow run not found","instance":"/api/v1/data/0b7c6f0a-2a55-4f43-9a36-0c8f4d2b1e77/nodes/normalize/outputs/data"}""")]
      )
    ]
  )
  fun getData(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @Parameter(
      description = "Temporal **run id** of the run (`runId` of `GET /api/v1/workflow-runs/{workflowId}`, the first segment of the output location), not the workflow id. $ID_RULE",
      example = ApiExamples.RUN_ID
    )
    @PathVariable
    runId: String,
    @Parameter(description = "Id of the node in the workflow. $ID_RULE", example = "normalize")
    @PathVariable
    nodeId: String,
    @Parameter(description = "Output port id of the node. $ID_RULE", example = "data")
    @PathVariable
    outputId: String,
    @Parameter(description = "Zero-based page number.", example = "0", schema = Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
    @RequestParam(defaultValue = "0")
    page: Int,
    @Parameter(
      description = "Rows per page.",
      example = "10",
      schema = Schema(type = "integer", format = "int32", minimum = "1", maximum = "${NodeOutputRepository.MAX_PAGE_SIZE}", defaultValue = "10")
    )
    @RequestParam(defaultValue = "10")
    pageSize: Int
  ): Page<List<CellDto>> {
    return nodeOutputService.getOutput(
      ownedBy = ownedBy,
      runId = runId,
      nodeId = nodeId,
      outputId = outputId,
      page = page,
      pageSize = pageSize
    )
  }
}
