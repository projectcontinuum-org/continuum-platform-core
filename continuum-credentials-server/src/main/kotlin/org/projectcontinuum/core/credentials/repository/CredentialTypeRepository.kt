package org.projectcontinuum.core.credentials.repository

import org.projectcontinuum.core.credentials.entity.CredentialTypeEntity
import java.util.UUID

interface CredentialTypeRepository {

  fun save(entity: CredentialTypeEntity): CredentialTypeEntity

  fun existsById(id: UUID): Boolean

  fun findByTypeAndVersion(type: String, version: String): CredentialTypeEntity?

  fun findAllByType(type: String): List<CredentialTypeEntity>

  fun findAll(): List<CredentialTypeEntity>

  fun existsByType(type: String): Boolean

  fun existsByTypeAndVersion(type: String, version: String): Boolean
}
