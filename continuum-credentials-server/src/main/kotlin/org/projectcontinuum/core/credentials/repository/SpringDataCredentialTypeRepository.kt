package org.projectcontinuum.core.credentials.repository

import org.projectcontinuum.core.credentials.entity.CredentialTypeEntity
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface SpringDataCredentialTypeRepository : CrudRepository<CredentialTypeEntity, UUID> {

  @Query("SELECT * FROM credential_types WHERE type = :type AND credential_type_version = :version LIMIT 1")
  fun findByTypeAndVersion(type: String, version: String): CredentialTypeEntity?

  @Query("SELECT * FROM credential_types WHERE type = :type ORDER BY created_at DESC")
  fun findAllByType(type: String): List<CredentialTypeEntity>

  @Query("SELECT COUNT(*) > 0 FROM credential_types WHERE type = :type")
  fun existsByType(type: String): Boolean

  @Query("SELECT COUNT(*) > 0 FROM credential_types WHERE type = :type AND credential_type_version = :version")
  fun existsByTypeAndVersion(type: String, version: String): Boolean
}
