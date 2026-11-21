package org.projectcontinuum.core.credentials.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(name = "CredentialCreateRequest", description = "A new credential for the caller.")
data class CredentialCreateRequest(
  @field:Schema(description = "Name, unique per user. Nodes reference the credential by this name.", example = "aws-prod")
  @field:NotBlank(message = "Name is required")
  @field:Pattern(
    regexp = "^[a-zA-Z0-9_-]+$",
    message = "Name must contain only alphanumeric characters, hyphens, and underscores"
  )
  @field:Size(max = 255, message = "Name must be at most 255 characters")
  val name: String,

  @field:Schema(description = "Credential type; must exist (see `GET /api/v1/credential-types`).", example = "GENERIC")
  @field:NotBlank(message = "Type is required")
  @field:Size(max = 50, message = "Type must be at most 50 characters")
  val type: String,

  @field:Schema(description = "Version of the credential type.", example = "1.0.0")
  @field:NotBlank(message = "Type version is required")
  @field:Size(max = 50, message = "Type version must be at most 50 characters")
  val typeVersion: String,

  @field:Schema(
    description = "The secret values: key → string value. Must not be empty and must satisfy the type's schema. " +
      "Each value is encrypted at rest.",
    example = "{\"accessKeyId\": \"AKIAIOSFODNN7EXAMPLE\", \"secretAccessKey\": \"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}"
  )
  @field:NotEmpty(message = "Data must not be empty")
  val data: Map<String, String>,

  @field:Schema(description = "Free-text description, at most 1000 characters.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "Read-only access to the analytics bucket")
  @field:Size(max = 1000, message = "Description must be at most 1000 characters")
  val description: String? = null
)
