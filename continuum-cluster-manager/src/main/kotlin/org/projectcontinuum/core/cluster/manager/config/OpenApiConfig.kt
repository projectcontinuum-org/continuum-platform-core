package org.projectcontinuum.core.cluster.manager.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
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
import org.springframework.http.HttpStatus

/**
 * OpenAPI metadata, groups and the shared error contract of the cluster manager. Conventions:
 * `docs/api/CONVENTIONS.md`. Off when `springdoc.api-docs.enabled=false` (`CONTINUUM_API_DOCS_ENABLED`).
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

    /** Called by Traefik ForwardAuth only. Never part of the public (or ungrouped) spec. */
    val INTERNAL_PATHS = arrayOf(ForwardedHeadersConfig.AUTHORIZE_PATH)

    const val USER_ID_SECURITY_SCHEME = "continuumUserId"
    const val PROBLEM_DETAIL_SCHEMA = "ProblemDetail"
    const val PROBLEM_JSON = "application/problem+json"
    const val TAG_WORKBENCHES = "Workbenches"
    const val TAG_WORKBENCH_CATALOG = "Workbench catalog"
    const val TAG_ACCESS_CONTROL = "Workbench access control"

    private const val COMPONENT_SCHEMAS_PREFIX = "#/components/schemas/"
  }

  @Bean
  fun continuumClusterManagerOpenApi(buildProperties: ObjectProvider<BuildProperties>): OpenAPI {
    val platformVersion = buildProperties.ifAvailable?.version ?: "dev"
    return OpenAPI()
      .info(
        Info()
          .title("Continuum Cluster Manager")
          .version(platformVersion)
          .description(
            """
            Manages the per-user Continuum workbenches (the browser IDE where workflows are authored) on Kubernetes.
            Each workbench is a PersistentVolumeClaim (the user's home and workspace), a Deployment, a Service and an
            Ingress in the workbench namespace, created from templates and optional overlay variants. Suspending a
            workbench removes everything but its volume; resuming recreates it.

            **Identity.** Continuum has no built-in authentication. The caller is the `x-continuum-user-id` header,
            set by the trusted reverse proxy; every workbench operation only sees the caller's own workbenches.
            Without the header the caller is `anonymous` (local development).

            **Errors** are RFC 9457 problem details (`application/problem+json`). They also carry the message in a
            deprecated `error` property for clients of the former `{"error": "..."}` bodies.
            """.trimIndent()
          )
          .license(License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0"))
          .contact(Contact().name("Project Continuum").url("https://github.com/projectcontinuum-org/continuum-platform-core"))
      )
      .externalDocs(ExternalDocumentation().description("Continuum documentation").url("https://docs.projectcontinuum.org"))
      .tags(
        listOf(
          Tag().name(TAG_WORKBENCHES).description("Create, inspect, suspend, resume, update and delete the caller's workbenches."),
          Tag().name(TAG_WORKBENCH_CATALOG).description("Images and overlay variants a workbench can be created with. Not user-scoped.")
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
                "Caller identity (owner of workbenches). Continuum has no built-in authentication: this header is set " +
                  "by the trusted reverse proxy in front of the service from the authenticated user, and the proxy " +
                  "must strip any value sent by the client. Without it the caller is `anonymous` (local development " +
                  "only). 1-255 characters without control characters, quotes or backslashes."
              )
          )
          .addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema())
      )
  }

  @Bean
  fun publicApiGroup(): GroupedOpenApi = GroupedOpenApi.builder()
    .group(PUBLIC_GROUP)
    .displayName("Continuum Cluster Manager")
    .pathsToMatch("/api/**")
    .pathsToExclude(*INTERNAL_PATHS)
    .build()

  @Bean
  @ConditionalOnBooleanProperty(name = ["continuum.core.api-docs.internal-group-enabled"])
  fun internalApiGroup(): GroupedOpenApi = GroupedOpenApi.builder()
    .group(INTERNAL_GROUP)
    .displayName("Continuum Cluster Manager (internal)")
    .pathsToMatch(*INTERNAL_PATHS)
    .addOpenApiCustomizer { openApi ->
      openApi.tags = listOf(
        Tag().name(TAG_ACCESS_CONTROL).description("Called by the Traefik ForwardAuth middleware in front of every workbench.")
      )
    }
    .build()

  /** The ungrouped `/v3/api-docs` must not reveal internal paths either. A no-op for the internal group. */
  @Bean
  fun hideInternalPathsFromUngroupedSpec() = GlobalOpenApiCustomizer { openApi ->
    val internalGroupOnly = openApi.paths?.keys?.all { path -> path in INTERNAL_PATHS } == true
    if (!internalGroupOnly) {
      openApi.paths?.keys?.removeIf { path -> path in INTERNAL_PATHS }
    }
  }

  /** A fixed absolute server URL when configured; otherwise springdoc derives it from the (forwarded) request. */
  @Bean
  fun fixedServerUrlCustomizer() = GlobalOpenApiCustomizer { openApi ->
    if (fixedServerUrl.isNotBlank()) {
      openApi.servers = listOf(Server().url(fixedServerUrl.trimEnd('/').ifEmpty { "/" }).description("Continuum Cluster Manager"))
    }
  }

  /** Adds `406` and `500` to every operation and `400` (malformed request) to operations with parameters or a body. */
  @Bean
  fun sharedErrorResponses() = GlobalOperationCustomizer { operation, _ ->
    val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
    if (takesInput(operation) && !responses.containsKey("400")) {
      responses.addApiResponse(
        "400",
        problemResponse(
          status = 400,
          description = "Malformed request: an invalid `x-continuum-user-id`, a missing or unparsable parameter, or a body that is not valid JSON.",
          exampleDetail = "Invalid user id: must be 1-255 characters without control characters, quotes or backslashes"
        )
      )
    }
    // Every operation declares the media type it produces, so an unsupported `Accept` is a real 406 (problem+json).
    if (!responses.containsKey("406")) {
      responses.addApiResponse(
        "406",
        problemResponse(
          status = 406,
          description = "The `Accept` header asks for a media type this operation does not produce.",
          exampleDetail = "Acceptable representations: [application/json]."
        )
      )
    }
    if (!responses.containsKey("500")) {
      responses.addApiResponse(
        "500",
        problemResponse(
          status = 500,
          description = "Kubernetes or the database failed, or an unexpected error occurred.",
          exampleDetail = "Kubernetes operation failed: connection refused"
        )
      )
    }
    operation
  }

  private fun takesInput(operation: Operation): Boolean =
    !operation.parameters.isNullOrEmpty() || operation.requestBody != null || operation.security?.isNotEmpty() == true

  private fun problemResponse(status: Int, description: String, exampleDetail: String): ApiResponse {
    val example = mapOf(
      "type" to "about:blank",
      "title" to HttpStatus.valueOf(status).reasonPhrase,
      "status" to status,
      "detail" to exampleDetail,
      "instance" to "/api/v1/workbench",
      "error" to exampleDetail
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
    .addProperty("detail", StringSchema().example("Workbench 'analysis' not found for user 'alice'").description("Human-readable explanation of this occurrence."))
    .addProperty("instance", StringSchema().format("uri-reference").example("/api/v1/workbench/analysis").description("Path of the request that produced the problem."))
    .addProperty(
      "error",
      StringSchema().example("Workbench 'analysis' not found for user 'alice'").deprecated(true)
        .description("Deprecated: the same text as `detail`, for clients of the former `{\"error\": \"...\"}` bodies.")
    )
    .required(listOf("type", "title", "status", "detail", "error"))
}
