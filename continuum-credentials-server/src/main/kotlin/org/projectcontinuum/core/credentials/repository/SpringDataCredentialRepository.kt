package org.projectcontinuum.core.credentials.repository

import org.projectcontinuum.core.credentials.entity.CredentialEntity
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface SpringDataCredentialRepository : CrudRepository<CredentialEntity, UUID> {

  @Query("SELECT * FROM credentials WHERE user_id = :userId AND name = :name LIMIT 1")
  fun findByUserIdAndName(userId: String, name: String): CredentialEntity?

  @Query("SELECT * FROM credentials WHERE user_id = :userId ORDER BY created_at DESC")
  fun findAllByUserId(userId: String): List<CredentialEntity>

  @Query("SELECT * FROM credentials WHERE user_id = :userId AND type = :type ORDER BY created_at DESC")
  fun findAllByUserIdAndType(userId: String, type: String): List<CredentialEntity>

  @Query("SELECT * FROM credentials WHERE user_id = :userId AND type = :type AND type_version = :typeVersion ORDER BY created_at DESC")
  fun findAllByUserIdAndTypeAndTypeVersion(userId: String, type: String, typeVersion: String): List<CredentialEntity>

  @Modifying
  @Query("DELETE FROM credentials WHERE user_id = :userId AND name = :name")
  fun deleteByUserIdAndName(userId: String, name: String)

  @Query("SELECT COUNT(*) > 0 FROM credentials WHERE user_id = :userId AND name = :name")
  fun existsByUserIdAndName(userId: String, name: String): Boolean
}
