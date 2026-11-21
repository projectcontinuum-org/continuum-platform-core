package org.projectcontinuum.core.credentials.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.projectcontinuum.core.credentials.config.OpenApiConfig
import org.projectcontinuum.core.credentials.model.CredentialTypeCreateRequest
import org.projectcontinuum.core.credentials.model.CredentialTypeResponse
import org.projectcontinuum.core.credentials.service.CredentialTypeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

/** Credential types are shared by all users: not user-scoped, so no operation carries a security requirement. */
@RestController
@RequestMapping("/api/v1/credential-types", produces = ["application/json"])
@Tag(name = OpenApiConfig.TAG_CREDENTIAL_TYPES)
class CredentialTypeController(
  private val credentialTypeService: CredentialTypeService
) {

  companion object {
    private const val NOT_USER_SCOPED = "Not user-scoped: every caller sees the same types."
    private const val TYPE_NOT_FOUND =
      """{"type":"about:blank","title":"Not Found","status":404,"detail":"Credential type 'postgres' not found","instance":"/api/v1/credential-types/postgres","error":"Credential type 'postgres' not found"}"""
  }

  @PostMapping(consumes = ["application/json"])
  @Operation(
    operationId = "createCredentialType",
    summary = "Register a credential type version",
    description = """
Adds a version of a credential type: the JSON Schema (draft 4) that credential `data` of this type must satisfy, and
the JSONForms UI schema the management UI renders the form from. Types are immutable: there is no update or delete.
The same type with an identical schema and UI schema cannot be registered under a second version. Any caller can
register types (TODO: restrict to operators?). $NOT_USER_SCOPED"""
  )
  @OpenApiRequestBody(
    description = "The type version.",
    required = true,
    content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "postgres", summary = "PostgreSQL connection", value = CredentialApiExamples.CREATE_TYPE_REQUEST)])]
  )
  @ApiResponse(responseCode = "201", description = "Registered.", content = [Content(mediaType = "application/json", schema = Schema(implementation = CredentialTypeResponse::class))])
  @ApiResponse(
    responseCode = "400",
    description = "A field is invalid (`fieldErrors` lists each one), or the body is malformed.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA))]
  )
  @ApiResponse(
    responseCode = "409",
    description = "This type version exists, or the same type and schemas exist under another version.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [ExampleObject(value = """{"type":"about:blank","title":"Conflict","status":409,"detail":"Credential type 'postgres' version '1.0.0' already exists","instance":"/api/v1/credential-types","error":"Credential type 'postgres' version '1.0.0' already exists"}""")]
      )
    ]
  )
  fun createType(
    @Valid @RequestBody request: CredentialTypeCreateRequest
  ): ResponseEntity<CredentialTypeResponse> {
    val response = credentialTypeService.createType(request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }

  @GetMapping
  @Operation(
    operationId = "listCredentialTypes",
    summary = "List every credential type version",
    description = "Returns all type versions, including the built-in `GENERIC` 1.0.0 (any string values). $NOT_USER_SCOPED Read-only."
  )
  @ApiResponse(
    responseCode = "200",
    description = "The type versions.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = CredentialTypeResponse::class)))]
  )
  fun listTypes(): ResponseEntity<List<CredentialTypeResponse>> {
    val response = credentialTypeService.listTypes()
    return ResponseEntity.ok(response)
  }

  @GetMapping("/{type}")
  @Operation(
    operationId = "listCredentialTypeVersions",
    summary = "List the versions of one credential type",
    description = "Returns every version of a credential type. $NOT_USER_SCOPED Read-only."
  )
  @ApiResponse(
    responseCode = "200",
    description = "The versions (never empty).",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = CredentialTypeResponse::class)))]
  )
  @ApiResponse(
    responseCode = "404",
    description = "No such credential type.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = TYPE_NOT_FOUND)])]
  )
  fun getTypeVersions(
    @Parameter(description = "Credential type.", example = "GENERIC")
    @PathVariable type: String
  ): ResponseEntity<List<CredentialTypeResponse>> {
    val response = credentialTypeService.getTypeVersions(type)
    return ResponseEntity.ok(response)
  }

  @GetMapping("/{type}/{version}")
  @Operation(
    operationId = "getCredentialTypeVersion",
    summary = "Get one credential type version",
    description = "Returns one type version with its schema and UI schema. $NOT_USER_SCOPED Read-only."
  )
  @ApiResponse(responseCode = "200", description = "The type version.", content = [Content(mediaType = "application/json", schema = Schema(implementation = CredentialTypeResponse::class))])
  @ApiResponse(
    responseCode = "404",
    description = "No such type or version.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = TYPE_NOT_FOUND)])]
  )
  fun getType(
    @Parameter(description = "Credential type.", example = "GENERIC")
    @PathVariable type: String,
    @Parameter(description = "Type version.", example = "1.0.0")
    @PathVariable version: String
  ): ResponseEntity<CredentialTypeResponse> {
    val response = credentialTypeService.getType(type, version)
    return ResponseEntity.ok(response)
  }
}
