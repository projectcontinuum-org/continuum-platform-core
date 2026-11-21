package org.projectcontinuum.core.api.server.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.projectcontinuum.core.api.server.config.ApiTags
import org.projectcontinuum.core.api.server.config.OpenApiConfig
import org.projectcontinuum.core.api.server.exception.ResourceNotFoundException
import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

/** The node registry. Not user-scoped: every caller sees the same nodes, so no operation carries a security requirement. */
@RestController
@RequestMapping("/api/v1/node-explorer", produces = ["application/json"])
@Tag(name = ApiTags.NODE_EXPLORER)
class NodeExplorerController(
    private val nodeExplorerService: NodeExplorerService
) {

    companion object {
        private const val NOT_USER_SCOPED = "Not user-scoped: every caller sees the same registry."
        private const val DOCUMENTATION_NOT_FOUND =
            """{"type":"about:blank","title":"Not Found","status":404,"detail":"No documentation for this node","instance":"/api/v1/node-explorer/nodes/org.example.UnknownNodeModel/documentation"}"""
        private const val DOCUMENTATION_EXAMPLE = "# Text Normalizer\n\nTrims, lowercases and removes non-alphanumeric characters from `inputCol` into `outputCol`."
    }

    @GetMapping("/children")
    @Operation(
        operationId = "listNodeExplorerChildren",
        summary = "List one level of the node palette",
        description = """
Returns the categories and nodes directly under a category of the node palette: categories first (`type: CATEGORY`,
`id` = category path to pass as `parentId`), then nodes (`type: NODE`, `id` = node id, `nodeInfo` = the registered
definition). The workbench's node explorer loads the palette lazily with this. An unknown category is an empty list.
$NOT_USER_SCOPED Read-only."""
    )
    @ApiResponse(
        responseCode = "200",
        description = "Categories, then nodes.",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = NodeExplorerTreeItem::class)))]
    )
    fun getChildren(
        @Parameter(description = "Category path (`/`-separated, as returned in `id`); empty for the root.", example = "Table & Data Structures")
        @RequestParam(required = false, defaultValue = "")
        parentId: String
    ): List<NodeExplorerTreeItem> {
        return nodeExplorerService.getChildren(parentId)
    }

    @GetMapping("/search")
    @Operation(
        operationId = "searchNodes",
        summary = "Search registered nodes",
        description = """
Finds registered nodes whose name, description, documentation or node id contains `query` (case-insensitive), and
returns them inside their category path (each category has `children`). A blank query returns an empty list.
$NOT_USER_SCOPED Read-only."""
    )
    @ApiResponse(
        responseCode = "200",
        description = "Matches grouped under their categories.",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = NodeExplorerTreeItem::class)))]
    )
    fun search(
        @Parameter(
            description = "Text to look for. `%` and `_` match any characters (SQL LIKE wildcards; TODO: probably unintended).",
            example = "normalize",
            required = true
        )
        @RequestParam query: String
    ): List<NodeExplorerTreeItem> {
        return nodeExplorerService.search(query)
    }

    @GetMapping("/nodes/{nodeId}/documentation", produces = [MediaType.TEXT_MARKDOWN_VALUE])
    @Operation(
        operationId = "getNodeDocumentation",
        summary = "Get a node's documentation",
        description = """
Returns the Markdown documentation the node's worker registered. Cached for a short time (30 s by default), so a
re-registered node's documentation can lag that long. $NOT_USER_SCOPED Read-only."""
    )
    @ApiResponse(
        responseCode = "200",
        description = "The documentation.",
        content = [Content(mediaType = MediaType.TEXT_MARKDOWN_VALUE, schema = Schema(type = "string"), examples = [ExampleObject(value = DOCUMENTATION_EXAMPLE)])]
    )
    @ApiResponse(
        responseCode = "404",
        description = "The node is not registered, or has no documentation.",
        content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = DOCUMENTATION_NOT_FOUND)])]
    )
    fun getDocumentation(
        @Parameter(description = "Node id (fully qualified class name of the node model).", example = ApiExamples.TEXT_NORMALIZER_NODE)
        @PathVariable nodeId: String
    ): String {
        return nodeExplorerService.getDocumentation(nodeId)
            ?: throw ResourceNotFoundException("No documentation for this node")
    }

    @GetMapping("/nodes/documentation", produces = [MediaType.TEXT_MARKDOWN_VALUE])
    @Operation(
        operationId = "getNodeDocumentationByQuery",
        summary = "Get a node's documentation (node id as a query parameter)",
        description = """
Same as `GET /api/v1/node-explorer/nodes/{nodeId}/documentation`, for node ids that a proxy might alter in a path.
$NOT_USER_SCOPED Read-only."""
    )
    @ApiResponse(
        responseCode = "200",
        description = "The documentation.",
        content = [Content(mediaType = MediaType.TEXT_MARKDOWN_VALUE, schema = Schema(type = "string"), examples = [ExampleObject(value = DOCUMENTATION_EXAMPLE)])]
    )
    @ApiResponse(
        responseCode = "404",
        description = "The node is not registered, or has no documentation.",
        content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = DOCUMENTATION_NOT_FOUND)])]
    )
    fun getDocumentationByParam(
        @Parameter(description = "Node id (fully qualified class name of the node model).", example = ApiExamples.TEXT_NORMALIZER_NODE, required = true)
        @RequestParam nodeId: String
    ): String {
        return nodeExplorerService.getDocumentation(nodeId)
            ?: throw ResourceNotFoundException("No documentation for this node")
    }

    @PostMapping("/nodes/task-queues", consumes = ["application/json"])
    @Operation(
        operationId = "resolveNodeTaskQueues",
        summary = "Resolve node ids to the task queues of their workers",
        description = """
Maps each node id to the Temporal task queue of the feature worker that registered it. The orchestration service calls
this before dispatching a run's nodes; a node id that is not registered is absent from the result (the orchestrator
retries until its worker registers). $NOT_USER_SCOPED Read-only and idempotent."""
    )
    @OpenApiRequestBody(
        description = "Node ids to resolve.",
        required = true,
        content = [
            Content(
                mediaType = "application/json",
                array = ArraySchema(schema = Schema(type = "string")),
                examples = [ExampleObject(value = """["${ApiExamples.CREATE_TABLE_NODE}", "${ApiExamples.TEXT_NORMALIZER_NODE}"]""")]
            )
        ]
    )
    @ApiResponse(
        responseCode = "200",
        description = "Node id → task queue, for the registered ids only.",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(type = "object", additionalPropertiesSchema = String::class),
                examples = [ExampleObject(value = """{"${ApiExamples.CREATE_TABLE_NODE}": "continuum-feature-base", "${ApiExamples.TEXT_NORMALIZER_NODE}": "continuum-feature-base"}""")]
            )
        ]
    )
    fun getTaskQueues(
        @RequestBody nodeIds: List<String>
    ): Map<String, String> {
        return nodeExplorerService.getTaskQueues(nodeIds)
    }
}
