package org.projectcontinuum.core.credentials.repository

import org.projectcontinuum.core.credentials.entity.CredentialEntity

class PostgresCredentialRepository(
  private val springDataRepository: SpringDataCredentialRepository
) : CredentialRepository {

  override fun save(entity: CredentialEntity): CredentialEntity =
    springDataRepository.save(entity)

  override fun findByUserIdAndName(userId: String, name: String): CredentialEntity? =
    springDataRepository.findByUserIdAndName(userId, name)

  override fun findAllByUserId(userId: String): List<CredentialEntity> =
    springDataRepository.findAllByUserId(userId)

  override fun findAllByUserIdAndType(userId: String, type: String): List<CredentialEntity> =
    springDataRepository.findAllByUserIdAndType(userId, type)

  override fun findAllByUserIdAndTypeAndTypeVersion(userId: String, type: String, typeVersion: String): List<CredentialEntity> =
    springDataRepository.findAllByUserIdAndTypeAndTypeVersion(userId, type, typeVersion)

  override fun deleteByUserIdAndName(userId: String, name: String) =
    springDataRepository.deleteByUserIdAndName(userId, name)

  override fun existsByUserIdAndName(userId: String, name: String): Boolean =
    springDataRepository.existsByUserIdAndName(userId, name)
}
