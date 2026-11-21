package org.projectcontinuum.core.api.server.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springdoc.core.customizers.GlobalOperationCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI metadata, groups and the shared error contract of the API server. Conventions:
 * `docs/api/CONVENTIONS.md`. Everything here is off when `springdoc.api-docs.enabled=false`
 * (`CONTINUUM_API_DOCS_ENABLED`), which the Helm chart sets in production.
 */
@Configuration
@ConditionalOnBooleanProperty(name = ["springdoc.api-docs.enabled"], matchIfMissing = true)
class OpenApiConfig(
  @param:Value("\${continuum.core.api-docs.server-url:}")
  private val fixedServerUrl: String
) {

  companion object {
    const val PUBLIC_GROUP = "public"
    const val INTERNAL_GROUP = "internal"

    /** Paths that only operators or the platform itself call. Never part of the public (or ungrouped) spec. */
    val INTERNAL_PATHS = arrayOf("/api/v1/admin/**")

    const val USER_ID_SECURITY_SCHEME = "continuumUserId"
    const val USER_GROUPS_SECURITY_SCHEME = "continuumUserGroups"

    const val PROBLEM_DETAIL_SCHEMA = "ProblemDetail"
    const val WORKFLOW_VALIDATION_PROBLEM_SCHEMA = "WorkflowValidationProblem"
    const val WORKFLOW_ISSUE_SCHEMA = "WorkflowIssue"
    const val PROBLEM_JSON = "application/problem+json"

    private const val COMPONENT_SCHEMAS_PREFIX = "#/components/schemas/"
  }

  @Bean
  fun continuumApiServerOpenApi(buildProperties: ObjectProvider<BuildProperties>): OpenAPI {
    val platformVersion = buildProperties.ifAvailable?.version ?: "dev"
    return OpenAPI()
      .info(
        Info()
          .title("Continuum API Server")
          .version(platformVersion)
          .description(
            """
            REST API of the Continuum workflow platform. It starts, schedules, stops and inspects runs of workflows
            authored in the Continuum workbench (`.cwf` files), serves the node registry (the palette of nodes that
            feature workers registered) and pages through node output tables stored as Parquet in S3/MinIO.

            Runs execute as Temporal workflows (`continuum-orchestration-service`) whose nodes run on feature workers.
            Live run status is not polled here: it streams over MQTT on `continuum/workflow/execution/{workflowId}/update`
            and is mirrored into the run endpoints of this API.

            **Identity.** Continuum has no built-in authentication. The caller is the `x-continuum-user-id` header,
            set by the trusted reverse proxy in front of the platform; every user-scoped endpoint only sees the
            caller's own runs and schedules. Without the header the caller is `anonymous` (local development).

            **Errors** are RFC 9457 problem details (`application/problem+json`).

            The same server also exposes the node registry to AI agents as an MCP server at `/mcp` (not described here).
            """.trimIndent()
          )
          .license(License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0"))
          .contact(Contact().name("Project Continuum").url("https://github.com/projectcontinuum-org/continuum-platform-core"))
      )
      .externalDocs(ExternalDocumentation().description("Continuum documentation").url("https://docs.projectcontinuum.org"))
      .tags(
        listOf(
          Tag().name(ApiTags.WORKFLOWS).description("Start, validate, stop and query workflow runs through Temporal."),
          Tag().name(ApiTags.WORKFLOW_RUNS).description(
            "Run history from the database: one row per run, kept up to date by the message bridge from the run's events."
          ),
          Tag().name(ApiTags.WORKFLOW_SCHEDULES).description("Cron schedules (Temporal schedules) that start a workflow repeatedly."),
          Tag().name(ApiTags.NODE_OUTPUTS).description("Paged reads of node output tables (Parquet in S3/MinIO)."),
          Tag().name(ApiTags.NODE_EXPLORER).description(
            "The node registry: nodes that feature workers registered, their documentation and task queues. Not user-scoped."
          )
          // The Administration tag is only added to the internal group: the public spec must not hint that it exists.
        )
      )
      .components(
        Components()
          .addSecuritySchemes(
            USER_ID_SECURITY_SCHEME,
            SecurityScheme()
              .type(SecurityScheme.Type.APIKEY)
              .`in`(SecurityScheme.In.HEADER)
              .name("x-continuum-user-id")
              .description(
                "Caller identity (owner of runs and schedules). Continuum has no built-in authentication: this header " +
                  "is set by the trusted reverse proxy in front of the service from the authenticated user, and the " +
                  "proxy must strip any value sent by the client. Without it the caller is `anonymous` " +
                  "(local development only)."
              )
          )
          .addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema())
          .addSchemas(WORKFLOW_ISSUE_SCHEMA, workflowIssueSchema())
          .addSchemas(WORKFLOW_VALIDATION_PROBLEM_SCHEMA, workflowValidationProblemSchema())
      )
  }

  @Bean
  fun publicApiGroup(): GroupedOpenApi = GroupedOpenApi.builder()
    .group(PUBLIC_GROUP)
    .displayName("Continuum API Server")
    .pathsToMatch("/api/**")
    .pathsToExclude(*INTERNAL_PATHS)
    .build()

  @Bean
  @ConditionalOnBooleanProperty(name = ["continuum.core.api-docs.internal-group-enabled"])
  fun internalApiGroup(): GroupedOpenApi = GroupedOpenApi.builder()
    .group(INTERNAL_GROUP)
    .displayName("Continuum API Server (internal)")
    .pathsToMatch(*INTERNAL_PATHS)
    .addOpenApiCustomizer { openApi ->
      // The groups header is only read by the administration endpoints, so only the internal spec describes it.
      openApi.components.addSecuritySchemes(
        USER_GROUPS_SECURITY_SCHEME,
        SecurityScheme()
          .type(SecurityScheme.Type.APIKEY)
          .`in`(SecurityScheme.In.HEADER)
          .name("x-continuum-user-groups")
          .description(
            "Comma-separated groups of the caller, set by the trusted reverse proxy (which must strip client-sent " +
              "values). Only the administration endpoints read it: one group must be in `CONTINUUM_ADMIN_GROUPS`."
          )
      )
      openApi.tags = listOf(
        Tag().name(ApiTags.ADMINISTRATION).description(
          "Operator endpoints across all tenants. Undiscoverable (404) unless the caller is in an admin group."
        )
      )
    }
    .build()

  /**
   * The ungrouped `/v3/api-docs` must not reveal internal paths either. It is built from every handler, so they are
   * removed here; the internal group only matches internal paths, so this is a no-op for it.
   */
  @Bean
  fun hideInternalPathsFromUngroupedSpec() = GlobalOpenApiCustomizer { openApi ->
    val internalGroupOnly = openApi.paths?.keys?.all { path -> isInternalPath(path) } == true
    if (!internalGroupOnly) {
      openApi.paths?.keys?.removeIf { path -> isInternalPath(path) }
    }
  }

  /** A fixed absolute server URL when configured; otherwise springdoc derives it from the (forwarded) request. */
  @Bean
  fun fixedServerUrlCustomizer() = GlobalOpenApiCustomizer { openApi ->
    if (fixedServerUrl.isNotBlank()) {
      openApi.servers = listOf(Server().url(fixedServerUrl.trimEnd('/').ifEmpty { "/" }).description("Continuum API Server"))
    }
  }

  /**
   * Adds the shared error responses every operation can return: `500` always, and `400` (malformed request) to
   * operations that take parameters or a body. An operation that documents its own `400`/`500` keeps it.
   */
  @Bean
  fun sharedErrorResponses() = GlobalOperationCustomizer { operation, _ ->
    val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
    if (takesInput(operation) && !responses.containsKey("400")) {
      responses.addApiResponse(
        "400",
        problemResponse(
          description = "Malformed request: a missing or unparsable parameter, or a body that is not valid JSON for this operation.",
          exampleDetail = "Required request parameter 'baseDir' for method parameter type String is not present"
        )
      )
    }
    if (!takesInput(operation) && responses.keys.none { status -> status.startsWith("4") }) {
      responses.addApiResponse(
        "406",
        problemResponse(
          description = "The `Accept` header asks for a media type this operation does not produce.",
          exampleDetail = "Acceptable representations: [application/json]."
        )
      )
    }
    if (!responses.containsKey("500")) {
      responses.addApiResponse(
        "500",
        problemResponse(
          description = "Unexpected server error, or a dependency (PostgreSQL, Temporal, S3) failed.",
          exampleDetail = "Internal server error"
        )
      )
    }
    operation
  }

  private fun isInternalPath(path: String): Boolean = path.startsWith("/api/v1/admin/") || path == "/api/v1/admin"

  private fun takesInput(operation: Operation): Boolean =
    !operation.parameters.isNullOrEmpty() || operation.requestBody != null

  private fun problemResponse(description: String, exampleDetail: String): ApiResponse {
    val status = when {
      exampleDetail == "Internal server error" -> 500
      exampleDetail.startsWith("Acceptable representations") -> 406
      else -> 400
    }
    val example = mapOf(
      "type" to "about:blank",
      "title" to when (status) {
        500 -> "Internal Server Error"
        406 -> "Not Acceptable"
        else -> "Bad Request"
      },
      "status" to status,
      "detail" to exampleDetail,
      "instance" to "/api/v1/workflow/tree"
    )
    return ApiResponse()
      .description(description)
      .content(
        Content().addMediaType(
          PROBLEM_JSON,
          MediaType()
            .schema(Schema<Any>().`$ref`(COMPONENT_SCHEMAS_PREFIX + PROBLEM_DETAIL_SCHEMA))
            .addExamples("error", Example().value(example))
        )
      )
  }

  private fun problemDetailSchema(): Schema<*> = ObjectSchema()
    .description("RFC 9457 problem detail, the error body of every Continuum service (`application/problem+json`).")
    .addProperty(
      "type",
      StringSchema().format("uri-reference").example("about:blank")
        .description("URI identifying the problem type; `about:blank` when only the status code matters.")
    )
    .addProperty("title", StringSchema().example("Not Found").description("Short summary of the problem type (the HTTP reason phrase by default)."))
    .addProperty("status", IntegerSchema().format("int32").example(404).description("HTTP status code of this response."))
    .addProperty("detail", StringSchema().example("Workflow run not found").description("Human-readable explanation of this occurrence."))
    .addProperty(
      "instance",
      StringSchema().format("uri-reference").example("/api/v1/workflow-runs/3f2c...")
        .description("Path of the request that produced the problem.")
    )
    .required(listOf("type", "title", "status"))

  private fun workflowIssueSchema(): Schema<*> = ObjectSchema()
    .description("One problem found by the workflow validator. `node`, `edge` and `property` say where it is, when that applies.")
    .addProperty(
      "message",
      StringSchema().example("Node 'normalize' (org.projectcontinuum.feature.base.analytics.node.TextNormalizerNodeModel): property 'inputCol' is required.")
        .description("Human-readable description of the issue.")
    )
    .addProperty("node", StringSchema().example("normalize").description("Id of the node concerned (`nodes[].id`). Absent when not node-specific."))
    .addProperty("edge", StringSchema().example("e1").description("Id of the edge concerned (`edges[].id`). Absent when not edge-specific."))
    .addProperty("property", StringSchema().example("inputCol").description("Top-level node property concerned. Absent when not property-specific."))
    .required(listOf("message"))

  private fun workflowValidationProblemSchema(): Schema<*> = ObjectSchema()
    .description(
      "Problem detail for a workflow that failed validation (title `Invalid workflow`). `detail` joins every error " +
        "message; `errors` and `warnings` list them one by one."
    )
    .allOf(listOf(Schema<Any>().`$ref`(COMPONENT_SCHEMAS_PREFIX + PROBLEM_DETAIL_SCHEMA)))
    .addProperty(
      "errors",
      ArraySchema().items(Schema<Any>().`$ref`(COMPONENT_SCHEMAS_PREFIX + WORKFLOW_ISSUE_SCHEMA))
        .description("Every error found. Any error rejects the workflow.")
    )
    .addProperty(
      "warnings",
      ArraySchema().items(Schema<Any>().`$ref`(COMPONENT_SCHEMAS_PREFIX + WORKFLOW_ISSUE_SCHEMA))
        .description("Findings that would not block a start, e.g. an input port with no incoming edge.")
    )
    .required(listOf("errors", "warnings"))
}

/** Tag names shared by the controllers and [OpenApiConfig]. */
object ApiTags {
  const val WORKFLOWS = "Workflows"
  const val WORKFLOW_RUNS = "Workflow runs"
  const val WORKFLOW_SCHEDULES = "Workflow schedules"
  const val NODE_OUTPUTS = "Node outputs"
  const val NODE_EXPLORER = "Node explorer"
  const val ADMINISTRATION = "Administration"
}
