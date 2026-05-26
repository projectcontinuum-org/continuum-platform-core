package org.projectcontinuum.core.credentials.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("credential_types")
data class CredentialTypeEntity(
  @Id
  @Column("credential_type_id")
  val credentialTypeId: UUID,
  @Column("type")
  val type: String,
  @Column("schema")
  val schema: JsonValue = JsonValue("{}"),
  @Column("ui_schema")
  val uiSchema: JsonValue = JsonValue("{}"),
  @Column("credential_type_version")
  val credentialTypeVersion: String = "1.0.0",
  @Column("created_at")
  val createdAt: Instant = Instant.now(),
  @Column("updated_at")
  val updatedAt: Instant = Instant.now(),
  @Version
  @Column("entity_version")
  val entityVersion: Long? = null
)
