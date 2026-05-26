package org.projectcontinuum.core.credentials.repository

import org.projectcontinuum.core.credentials.entity.CredentialEntity
import java.util.UUID

interface CredentialRepository {

  fun save(entity: CredentialEntity): CredentialEntity

  fun findByUserIdAndName(userId: String, name: String): CredentialEntity?

  fun findAllByUserId(userId: String): List<CredentialEntity>

  fun findAllByUserIdAndType(userId: String, type: String): List<CredentialEntity>

  fun findAllByUserIdAndTypeAndTypeVersion(userId: String, type: String, typeVersion: String): List<CredentialEntity>

  fun deleteByUserIdAndName(userId: String, name: String)

  fun existsByUserIdAndName(userId: String, name: String): Boolean
}
