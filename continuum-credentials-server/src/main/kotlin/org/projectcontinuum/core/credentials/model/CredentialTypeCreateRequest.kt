package org.projectcontinuum.core.credentials.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CredentialTypeCreateRequest(
  @field:NotBlank(message = "Type is required")
  @field:Pattern(
    regexp = "^[a-zA-Z0-9_-]+$",
    message = "Type must contain only alphanumeric characters, hyphens, and underscores"
  )
  @field:Size(max = 50, message = "Type must be at most 50 characters")
  val type: String,

  val schema: Map<String, Any?> = emptyMap(),

  val uiSchema: Map<String, Any?> = emptyMap(),

  @field:NotBlank(message = "Version is required")
  @field:Size(max = 50, message = "Version must be at most 50 characters")
  val version: String = "1.0.0"
)
