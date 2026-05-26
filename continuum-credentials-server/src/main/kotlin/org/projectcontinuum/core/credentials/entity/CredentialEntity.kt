package org.projectcontinuum.core.credentials.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("credentials")
data class CredentialEntity(
  @Id
  @Column("credential_id")
  val credentialId: UUID,
  @Column("user_id")
  val userId: String,
  @Column("name")
  val name: String,
  @Column("type")
  val type: String,
  @Column("type_version")
  val typeVersion: String,
  @Column("data")
  val data: JsonValue,
  @Column("description")
  val description: String? = null,
  @Column("created_by")
  val createdBy: String,
  @Column("updated_by")
  val updatedBy: String,
  @Column("created_at")
  val createdAt: Instant = Instant.now(),
  @Column("updated_at")
  val updatedAt: Instant = Instant.now(),
  @Column("last_accessed_at")
  val lastAccessedAt: Instant? = null,
  @Version
  @Column("entity_version")
  val entityVersion: Long? = null
)
