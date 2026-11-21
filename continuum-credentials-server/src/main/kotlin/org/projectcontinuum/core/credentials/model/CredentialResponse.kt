package org.projectcontinuum.core.credentials.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "Credential", description = "A credential of the caller, with its values decrypted.")
data class CredentialResponse(
  @field:Schema(description = "Owner (the `x-continuum-user-id` it was created with).", example = "alice")
  val userId: String,
  @field:Schema(description = "Name, unique per user.", example = "aws-prod")
  val name: String,
  @field:Schema(description = "Credential type.", example = "GENERIC")
  val type: String,
  @field:Schema(description = "Credential type version.", example = "1.0.0")
  val typeVersion: String,
  @field:Schema(description = "The secret values, decrypted: key → value.", example = "{\"accessKeyId\": \"AKIAIOSFODNN7EXAMPLE\", \"secretAccessKey\": \"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}")
  val data: Map<String, String>,
  @field:Schema(description = "Free-text description.", nullable = true, example = "Read-only access to the analytics bucket")
  val description: String?,
  @field:Schema(description = "User who created it.", example = "alice")
  val createdBy: String,
  @field:Schema(description = "User who last changed it.", example = "alice")
  val updatedBy: String,
  @field:Schema(description = "When it was created (ISO-8601, UTC).", example = "2026-11-21T09:00:00Z")
  val createdAt: Instant,
  @field:Schema(description = "When it was last changed (ISO-8601, UTC).", example = "2026-11-21T09:00:00Z")
  val updatedAt: Instant,
  @field:Schema(description = "When it was last returned by a read (ISO-8601, UTC); null if never read since creation.", nullable = true, example = "2026-11-21T10:12:00Z")
  val lastAccessedAt: Instant?
)
