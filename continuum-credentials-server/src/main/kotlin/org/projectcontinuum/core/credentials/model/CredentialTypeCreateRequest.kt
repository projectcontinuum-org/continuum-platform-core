package org.projectcontinuum.core.credentials.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(name = "CredentialTypeCreateRequest", description = "A new version of a credential type.")
data class CredentialTypeCreateRequest(
  @field:Schema(description = "Type name: 1-50 characters of `[a-zA-Z0-9_-]`.", example = "postgres")
  @field:NotBlank(message = "Type is required")
  @field:Pattern(
    regexp = "^[a-zA-Z0-9_-]+$",
    message = "Type must contain only alphanumeric characters, hyphens, and underscores"
  )
  @field:Size(max = 50, message = "Type must be at most 50 characters")
  val type: String,

  @field:Schema(description = "JSON Schema (draft 4) of a credential's `data` map. `{}` accepts any values.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "{}")
  val schema: Map<String, Any?> = emptyMap(),

  @field:Schema(description = "JSONForms UI schema the management UI renders the credential form from.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "{}")
  val uiSchema: Map<String, Any?> = emptyMap(),

  @field:Schema(description = "Version, at most 50 characters.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "1.0.0", example = "1.0.0")
  @field:NotBlank(message = "Version is required")
  @field:Size(max = 50, message = "Version must be at most 50 characters")
  val version: String = "1.0.0"
)
