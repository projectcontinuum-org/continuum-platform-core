package org.projectcontinuum.core.credentials.config

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
import org.springframework.http.HttpStatus

/**
 * OpenAPI metadata, the `public` group and the shared error contract of the credentials server. Conventions:
 * `docs/api/CONVENTIONS.md`. Off when `springdoc.api-docs.enabled=false` (`CONTINUUM_API_DOCS_ENABLED`). This service
 * has no internal endpoints, so there is no `internal` group.
 */
@Configuration
@ConditionalOnBooleanProperty(name = ["springdoc.api-docs.enabled"], matchIfMissing = true)
class OpenApiConfig(
  @param:Value("\${continuum.core.api-docs.server-url:}")
  private val fixedServerUrl: String
) {

  companion object {
    const val PUBLIC_GROUP = "public"
    const val USER_ID_SECURITY_SCHEME = "continuumUserId"
    const val PROBLEM_DETAIL_SCHEMA = "ProblemDetail"
    const val PROBLEM_JSON = "application/problem+json"
    const val TAG_CREDENTIALS = "Credentials"
    const val TAG_CREDENTIAL_TYPES = "Credential types"

    private const val COMPONENT_SCHEMAS_PREFIX = "#/components/schemas/"
  }

  @Bean
  fun continuumCredentialsServerOpenApi(buildProperties: ObjectProvider<BuildProperties>): OpenAPI {
    val platformVersion = buildProperties.ifAvailable?.version ?: "dev"
    return OpenAPI()
      .info(
        Info()
          .title("Continuum Credentials Server")
          .version(platformVersion)
          .description(
            """
            Stores the secrets Continuum workflow nodes need (API keys, database passwords, cloud credentials) so
            they never appear in a workflow. A workflow only names a credential; when a node runs, its feature worker
            fetches the credential by name, on behalf of the workflow's owner, with `GET /api/v1/credentials/{name}`.

            A credential is a flat map of string values, encrypted per value with AES-GCM before it is stored and
            returned decrypted to its owner. Each credential has a **credential type** (with versions) whose JSON
            Schema (draft 4) the values must satisfy; the built-in `GENERIC` type accepts any string values.

            **Identity.** Continuum has no built-in authentication. The owner is the `x-continuum-user-id` header, set
            by the trusted reverse proxy; every credential endpoint only sees the caller's own credentials. Without the
            header the caller is `anonymous` (local development). Credential types are shared by all users.

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
          Tag().name(TAG_CREDENTIALS).description("The caller's credentials: create, read (decrypted), update and delete."),
          Tag().name(TAG_CREDENTIAL_TYPES).description("Credential types and their versions: the schema credential values must satisfy. Not user-scoped.")
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
                "Owner of the credentials. Continuum has no built-in authentication: this header is set by the trusted " +
                  "reverse proxy in front of the service from the authenticated user (feature workers send the " +
                  "workflow owner's id), and the proxy must strip any value sent by the client. Without it the caller " +
                  "is `anonymous` (local development only)."
              )
          )
          .addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema())
      )
  }

  @Bean
  fun publicApiGroup(): GroupedOpenApi = GroupedOpenApi.builder()
    .group(PUBLIC_GROUP)
    .displayName("Continuum Credentials Server")
    .pathsToMatch("/api/**")
    .build()

  /** A fixed absolute server URL when configured; otherwise springdoc derives it from the (forwarded) request. */
  @Bean
  fun fixedServerUrlCustomizer() = GlobalOpenApiCustomizer { openApi ->
    if (fixedServerUrl.isNotBlank()) {
      openApi.servers = listOf(Server().url(fixedServerUrl.trimEnd('/').ifEmpty { "/" }).description("Continuum Credentials Server"))
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
          description = "Malformed request: a missing or unparsable parameter, or a body that is not valid JSON.",
          exampleDetail = "Failed to read request"
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
          description = "The database or the encryption failed, or an unexpected error occurred.",
          exampleDetail = "Internal server error"
        )
      )
    }
    operation
  }

  private fun takesInput(operation: Operation): Boolean =
    !operation.parameters.isNullOrEmpty() || operation.requestBody != null

  private fun problemResponse(status: Int, description: String, exampleDetail: String): ApiResponse {
    val example = mapOf(
      "type" to "about:blank",
      "title" to HttpStatus.valueOf(status).reasonPhrase,
      "status" to status,
      "detail" to exampleDetail,
      "instance" to "/api/v1/credentials",
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
    .addProperty("detail", StringSchema().example("Credential 'aws-prod' not found").description("Human-readable explanation of this occurrence."))
    .addProperty("instance", StringSchema().format("uri-reference").example("/api/v1/credentials/aws-prod").description("Path of the request that produced the problem."))
    .addProperty(
      "error",
      StringSchema().example("Credential 'aws-prod' not found").deprecated(true)
        .description("Deprecated: the same text as `detail`, for clients of the former `{\"error\": \"...\"}` bodies.")
    )
    .addProperty(
      "fieldErrors",
      ArraySchema()
        .items(
          ObjectSchema()
            .addProperty("field", StringSchema().example("name").description("Request field that failed validation."))
            .addProperty("message", StringSchema().example("Name is required").description("What is wrong with it."))
            .required(listOf("field", "message"))
        )
        .description("Only on 400 for an invalid request body: one entry per violated constraint.")
    )
    .required(listOf("type", "title", "status", "detail", "error"))
}
