package org.projectcontinuum.core.credentials.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(name = "CredentialUpdateRequest", description = "Changes to a credential; omitted or null fields keep their value.")
data class CredentialUpdateRequest(
  @field:Schema(description = "New name (renames the credential; must be free for this user).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "aws-prod-2026")
  @field:Pattern(
    regexp = "^[a-zA-Z0-9_-]+$",
    message = "Name must contain only alphanumeric characters, hyphens, and underscores"
  )
  @field:Size(max = 255, message = "Name must be at most 255 characters")
  val name: String? = null,

  @field:Schema(description = "New credential type.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "GENERIC")
  @field:Size(max = 50, message = "Type must be at most 50 characters")
  val type: String? = null,

  @field:Schema(description = "New credential type version.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "1.0.0")
  @field:Size(max = 50, message = "Type version must be at most 50 characters")
  val typeVersion: String? = null,

  @field:Schema(description = "New values; replaces all current values and must satisfy the type's schema.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
  val data: Map<String, String>? = null,

  @field:Schema(description = "New description. Null keeps the current one (it cannot be cleared).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "Rotated on 2026-11-21")
  @field:Size(max = 1000, message = "Description must be at most 1000 characters")
  val description: String? = null
)
