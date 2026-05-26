package org.projectcontinuum.core.credentials.model

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CredentialUpdateRequest(
  @field:Pattern(
    regexp = "^[a-zA-Z0-9_-]+$",
    message = "Name must contain only alphanumeric characters, hyphens, and underscores"
  )
  @field:Size(max = 255, message = "Name must be at most 255 characters")
  val name: String? = null,

  @field:Size(max = 50, message = "Type must be at most 50 characters")
  val type: String? = null,

  @field:Size(max = 50, message = "Type version must be at most 50 characters")
  val typeVersion: String? = null,

  val data: Map<String, String>? = null,

  @field:Size(max = 1000, message = "Description must be at most 1000 characters")
  val description: String? = null
)
