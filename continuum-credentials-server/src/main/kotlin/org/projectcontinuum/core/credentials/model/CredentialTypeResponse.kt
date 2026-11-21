package org.projectcontinuum.core.credentials.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "CredentialType", description = "One version of a credential type.")
data class CredentialTypeResponse(
  @field:Schema(description = "Type name.", example = "GENERIC")
  val type: String,
  @field:Schema(description = "JSON Schema (draft 4) credential `data` of this type must satisfy.", example = "{\"type\": \"object\", \"additionalProperties\": {\"type\": \"string\"}}")
  val schema: Map<String, Any?>,
  @field:Schema(description = "JSONForms UI schema of the credential form; `{}` when none.")
  val uiSchema: Map<String, Any?>,
  @field:Schema(description = "Version.", example = "1.0.0")
  val version: String,
  @field:Schema(description = "When it was registered (ISO-8601, UTC).", example = "2026-11-01T00:00:00Z")
  val createdAt: Instant,
  @field:Schema(description = "Same as `createdAt` (types are immutable).", example = "2026-11-01T00:00:00Z")
  val updatedAt: Instant
)
