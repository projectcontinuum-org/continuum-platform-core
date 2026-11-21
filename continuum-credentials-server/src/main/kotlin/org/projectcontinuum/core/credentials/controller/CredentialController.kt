package org.projectcontinuum.core.credentials.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.projectcontinuum.core.credentials.config.OpenApiConfig
import org.projectcontinuum.core.credentials.model.CredentialCreateRequest
import org.projectcontinuum.core.credentials.model.CredentialResponse
import org.projectcontinuum.core.credentials.model.CredentialUpdateRequest
import org.projectcontinuum.core.credentials.service.CredentialService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody

@RestController
@RequestMapping("/api/v1/credentials", produces = ["application/json"])
@Tag(name = OpenApiConfig.TAG_CREDENTIALS)
@SecurityRequirement(name = OpenApiConfig.USER_ID_SECURITY_SCHEME)
class CredentialController(
  private val credentialService: CredentialService
) {

  companion object {
    private const val NAME_DESCRIPTION = "Credential name, unique per user: 1-255 characters of `[a-zA-Z0-9_-]`."
    private const val READ_SIDE_EFFECT =
      "Every returned credential's `lastAccessedAt` is set to now (one database update each)."
  }

  @PostMapping(consumes = ["application/json"])
  @Operation(
    operationId = "createCredential",
    summary = "Create a credential for the caller",
    description = """
Stores a new credential owned by the caller. `data` must satisfy the JSON Schema (draft 4) of the given credential type
version; each value is encrypted with AES-GCM before it is stored. Used by the management UI. Not idempotent: a
second create with the same name is a 409."""
  )
  @OpenApiRequestBody(
    description = "The credential.",
    required = true,
    content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "generic", summary = "AWS keys as a GENERIC credential", value = CredentialApiExamples.CREATE_CREDENTIAL_REQUEST)])]
  )
  @ApiResponse(
    responseCode = "201",
    description = "Created. The response contains the decrypted `data`.",
    content = [Content(mediaType = "application/json", schema = Schema(implementation = CredentialResponse::class), examples = [ExampleObject(value = CredentialApiExamples.CREDENTIAL_RESPONSE)])]
  )
  @ApiResponse(
    responseCode = "400",
    description = "A field is invalid (`fieldErrors` lists each one), `data` does not match the type's schema, or the body is malformed.",
    content = [
      Content(
        mediaType = OpenApiConfig.PROBLEM_JSON,
        schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA),
        examples = [
          ExampleObject(name = "invalidField", value = CredentialApiExamples.VALIDATION_PROBLEM),
          ExampleObject(name = "schemaMismatch", value = CredentialApiExamples.SCHEMA_PROBLEM)
        ]
      )
    ]
  )
  @ApiResponse(
    responseCode = "404",
    description = "The credential type version does not exist.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = CredentialApiExamples.TYPE_NOT_FOUND)])]
  )
  @ApiResponse(
    responseCode = "409",
    description = "The caller already has a credential with this name.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = CredentialApiExamples.CREDENTIAL_CONFLICT)])]
  )
  fun createCredential(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Valid @RequestBody request: CredentialCreateRequest
  ): ResponseEntity<CredentialResponse> {
    val response = credentialService.createCredential(userId, request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
  }

  @GetMapping
  @Operation(
    operationId = "listCredentials",
    summary = "List the caller's credentials",
    description = """
Returns all of the caller's credentials **with decrypted `data`**. The workbench's credential picker reads `name` and
`description` from it. $READ_SIDE_EFFECT"""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The credentials; empty when the caller has none.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = CredentialResponse::class)))]
  )
  fun listCredentials(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String
  ): ResponseEntity<List<CredentialResponse>> {
    val response = credentialService.listCredentials(userId)
    return ResponseEntity.ok(response)
  }

  @GetMapping("/{name}")
  @Operation(
    operationId = "getCredential",
    summary = "Get one of the caller's credentials with its decrypted values",
    description = """
Returns one credential with decrypted `data`. This is how a node gets its secret at run time: the feature worker calls
it from the node's `execute()` with the workflow owner as `x-continuum-user-id` and reads `data`. $READ_SIDE_EFFECT"""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The credential.",
    content = [Content(mediaType = "application/json", schema = Schema(implementation = CredentialResponse::class), examples = [ExampleObject(value = CredentialApiExamples.CREDENTIAL_RESPONSE)])]
  )
  @ApiResponse(
    responseCode = "404",
    description = "The caller has no credential with this name (other users' credentials are indistinguishable from unknown ones).",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = CredentialApiExamples.CREDENTIAL_NOT_FOUND)])]
  )
  fun getCredential(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = NAME_DESCRIPTION, example = "aws-prod")
    @PathVariable name: String
  ): ResponseEntity<CredentialResponse> {
    val response = credentialService.getCredential(userId, name)
    return ResponseEntity.ok(response)
  }

  @GetMapping("/type/{type}")
  @Operation(
    operationId = "listCredentialsByType",
    summary = "List the caller's credentials of one type",
    description = """
Returns the caller's credentials of a credential type (any version), with decrypted `data`. The workbench's credential
picker uses it when a node restricts the picker to a type. An unknown type is an empty list. $READ_SIDE_EFFECT"""
  )
  @ApiResponse(
    responseCode = "200",
    description = "The credentials; empty when none match.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = CredentialResponse::class)))]
  )
  fun getCredentialsByType(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = "Credential type.", example = "GENERIC")
    @PathVariable type: String
  ): ResponseEntity<List<CredentialResponse>> {
    val response = credentialService.getCredentialsByType(userId, type)
    return ResponseEntity.ok(response)
  }

  @GetMapping("/type/{type}/{version}")
  @Operation(
    operationId = "listCredentialsByTypeVersion",
    summary = "List the caller's credentials of one type version",
    description = "Like `GET /api/v1/credentials/type/{type}`, restricted to one type version. $READ_SIDE_EFFECT"
  )
  @ApiResponse(
    responseCode = "200",
    description = "The credentials; empty when none match.",
    content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = CredentialResponse::class)))]
  )
  fun getCredentialsByTypeAndVersion(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = "Credential type.", example = "GENERIC")
    @PathVariable type: String,
    @Parameter(description = "Credential type version.", example = "1.0.0")
    @PathVariable version: String
  ): ResponseEntity<List<CredentialResponse>> {
    val response = credentialService.getCredentialsByTypeAndVersion(userId, type, version)
    return ResponseEntity.ok(response)
  }

  @PutMapping("/{name}", consumes = ["application/json"])
  @Operation(
    operationId = "updateCredential",
    summary = "Update a credential",
    description = """
Changes the given fields of one of the caller's credentials; omitted fields keep their value. `name` renames it (nodes
that reference the old name then fail), `data` replaces all values and is checked against the (possibly new) type's
schema. A null `description` keeps the current one. Idempotent for the same body."""
  )
  @OpenApiRequestBody(
    description = "The fields to change.",
    required = true,
    content = [Content(mediaType = "application/json", examples = [ExampleObject(name = "rotate", summary = "Rotate the keys", value = CredentialApiExamples.UPDATE_CREDENTIAL_REQUEST)])]
  )
  @ApiResponse(responseCode = "200", description = "Updated.", content = [Content(mediaType = "application/json", schema = Schema(implementation = CredentialResponse::class))])
  @ApiResponse(
    responseCode = "400",
    description = "A field is invalid, `data` does not match the type's schema, or the body is malformed.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = CredentialApiExamples.SCHEMA_PROBLEM)])]
  )
  @ApiResponse(
    responseCode = "404",
    description = "The caller has no credential with this name, or the new type version does not exist.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = CredentialApiExamples.CREDENTIAL_NOT_FOUND)])]
  )
  @ApiResponse(
    responseCode = "409",
    description = "The new name is already used, or the credential was changed concurrently (retry).",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = CredentialApiExamples.CREDENTIAL_CONFLICT)])]
  )
  fun updateCredential(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = NAME_DESCRIPTION, example = "aws-prod")
    @PathVariable name: String,
    @Valid @RequestBody request: CredentialUpdateRequest
  ): ResponseEntity<CredentialResponse> {
    val response = credentialService.updateCredential(userId, name, request)
    return ResponseEntity.ok(response)
  }

  @DeleteMapping("/{name}")
  @Operation(
    operationId = "deleteCredential",
    summary = "Delete a credential",
    description = "Deletes one of the caller's credentials. Nodes that reference it fail on their next run. A second delete is a 404."
  )
  @ApiResponse(responseCode = "204", description = "Deleted.")
  @ApiResponse(
    responseCode = "404",
    description = "The caller has no credential with this name.",
    content = [Content(mediaType = OpenApiConfig.PROBLEM_JSON, schema = Schema(ref = OpenApiConfig.PROBLEM_DETAIL_SCHEMA), examples = [ExampleObject(value = CredentialApiExamples.CREDENTIAL_NOT_FOUND)])]
  )
  fun deleteCredential(
    @Parameter(hidden = true)
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") userId: String,
    @Parameter(description = NAME_DESCRIPTION, example = "aws-prod")
    @PathVariable name: String
  ): ResponseEntity<Void> {
    credentialService.deleteCredential(userId, name)
    return ResponseEntity.noContent().build()
  }
}
