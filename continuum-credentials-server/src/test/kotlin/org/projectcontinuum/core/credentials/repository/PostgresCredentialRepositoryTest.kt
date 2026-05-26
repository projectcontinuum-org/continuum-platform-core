package org.projectcontinuum.core.credentials.repository

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.credentials.config.H2JdbcConfig
import org.projectcontinuum.core.credentials.entity.CredentialEntity
import org.projectcontinuum.core.credentials.entity.CredentialTypeEntity
import org.projectcontinuum.core.credentials.entity.JsonValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(H2JdbcConfig::class)
class PostgresCredentialRepositoryTest {

  @Autowired
  private lateinit var credentialRepository: CredentialRepository

  @Autowired
  private lateinit var credentialTypeRepository: CredentialTypeRepository

  @Autowired
  private lateinit var springDataCredentialRepository: SpringDataCredentialRepository

  @Autowired
  private lateinit var springDataCredentialTypeRepository: SpringDataCredentialTypeRepository

  @BeforeEach
  fun setUp() {
    springDataCredentialRepository.deleteAll()
    springDataCredentialTypeRepository.deleteAll()
    credentialTypeRepository.save(CredentialTypeEntity(credentialTypeId = UUID.randomUUID(), type = "s3", credentialTypeVersion = "1.0.0"))
    credentialTypeRepository.save(CredentialTypeEntity(credentialTypeId = UUID.randomUUID(), type = "s3", credentialTypeVersion = "2.0.0"))
    credentialTypeRepository.save(CredentialTypeEntity(credentialTypeId = UUID.randomUUID(), type = "git", credentialTypeVersion = "1.0.0"))
  }

  private fun createEntity(
    userId: String = "user-1",
    name: String = "test-cred",
    type: String = "s3",
    typeVersion: String = "1.0.0"
  ): CredentialEntity {
    return CredentialEntity(
      credentialId = UUID.randomUUID(),
      userId = userId,
      name = name,
      type = type,
      typeVersion = typeVersion,
      data = JsonValue("""{"accessKey":"enc-key"}"""),
      description = "Test",
      createdBy = userId,
      updatedBy = userId,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
  }

  @Test
  fun `save and findByUserIdAndName includes typeVersion`() {
    credentialRepository.save(createEntity())

    val found = credentialRepository.findByUserIdAndName("user-1", "test-cred")

    assertNotNull(found)
    assertEquals("s3", found!!.type)
    assertEquals("1.0.0", found.typeVersion)
  }

  @Test
  fun `credentials can reference different type versions`() {
    credentialRepository.save(createEntity(name = "cred-v1", typeVersion = "1.0.0"))
    credentialRepository.save(createEntity(name = "cred-v2", typeVersion = "2.0.0"))

    val v1 = credentialRepository.findByUserIdAndName("user-1", "cred-v1")
    val v2 = credentialRepository.findByUserIdAndName("user-1", "cred-v2")

    assertEquals("1.0.0", v1!!.typeVersion)
    assertEquals("2.0.0", v2!!.typeVersion)
  }

  @Test
  fun `findAllByUserIdAndType filters by type regardless of version`() {
    credentialRepository.save(createEntity(name = "s3-v1", type = "s3", typeVersion = "1.0.0"))
    credentialRepository.save(createEntity(name = "s3-v2", type = "s3", typeVersion = "2.0.0"))
    credentialRepository.save(createEntity(name = "git-cred", type = "git", typeVersion = "1.0.0"))

    val results = credentialRepository.findAllByUserIdAndType("user-1", "s3")
    assertEquals(2, results.size)
  }

  @Test
  fun `different users can have same credential name`() {
    credentialRepository.save(createEntity(userId = "user-1", name = "shared"))
    credentialRepository.save(createEntity(userId = "user-2", name = "shared"))

    assertNotNull(credentialRepository.findByUserIdAndName("user-1", "shared"))
    assertNotNull(credentialRepository.findByUserIdAndName("user-2", "shared"))
  }

  @Test
  fun `existsByUserIdAndName works correctly`() {
    credentialRepository.save(createEntity())
    assertTrue(credentialRepository.existsByUserIdAndName("user-1", "test-cred"))
    assertFalse(credentialRepository.existsByUserIdAndName("user-1", "nonexistent"))
  }

  @Test
  fun `deleteByUserIdAndName removes the credential`() {
    credentialRepository.save(createEntity())
    credentialRepository.deleteByUserIdAndName("user-1", "test-cred")
    assertNull(credentialRepository.findByUserIdAndName("user-1", "test-cred"))
  }

  @Test
  fun `save updates existing entity`() {
    val entity = credentialRepository.save(createEntity())
    credentialRepository.save(entity.copy(description = "Updated", updatedAt = Instant.now()))

    assertEquals("Updated", credentialRepository.findByUserIdAndName("user-1", "test-cred")!!.description)
  }
}
