package org.projectcontinuum.core.credentials.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.projectcontinuum.core.credentials.entity.CredentialEntity
import org.projectcontinuum.core.credentials.entity.CredentialTypeEntity
import org.projectcontinuum.core.credentials.entity.JsonValue
import org.projectcontinuum.core.credentials.exception.CredentialAlreadyExistsException
import org.projectcontinuum.core.credentials.exception.CredentialDataValidationException
import org.projectcontinuum.core.credentials.exception.CredentialNotFoundException
import org.projectcontinuum.core.credentials.exception.CredentialTypeNotFoundException
import org.projectcontinuum.core.credentials.model.CredentialCreateRequest
import org.projectcontinuum.core.credentials.model.CredentialUpdateRequest
import org.projectcontinuum.core.credentials.repository.CredentialRepository
import org.projectcontinuum.core.credentials.repository.CredentialTypeRepository
import java.time.Instant
import java.util.UUID

class CredentialServiceTest {

  private lateinit var credentialRepository: CredentialRepository
  private lateinit var credentialTypeRepository: CredentialTypeRepository
  private lateinit var encryptionService: EncryptionService
  private lateinit var credentialService: CredentialService
  private val objectMapper: ObjectMapper = jacksonObjectMapper()

  private val userId = "user-1"
  private val cipherPrefix = "{cipher}"

  private val s3Schema = """{"type":"object","properties":{"accessKey":{"type":"string"},"secretKey":{"type":"string"}},"required":["accessKey","secretKey"]}"""

  private fun s3TypeEntity() = CredentialTypeEntity(
    credentialTypeId = UUID.randomUUID(),
    type = "s3",
    schema = JsonValue(s3Schema),
    uiSchema = JsonValue("{}"),
    credentialTypeVersion = "1.0.0"
  )

  private fun emptySchemaTypeEntity() = CredentialTypeEntity(
    credentialTypeId = UUID.randomUUID(),
    type = "generic",
    schema = JsonValue("{}"),
    uiSchema = JsonValue("{}"),
    credentialTypeVersion = "1.0.0"
  )

  @BeforeEach
  fun setUp() {
    credentialRepository = mock()
    credentialTypeRepository = mock()
    encryptionService = mock()
    credentialService = CredentialService(credentialRepository, credentialTypeRepository, encryptionService, objectMapper)

    whenever(encryptionService.encrypt(any())).thenAnswer { "enc(${it.arguments[0]})" }
    whenever(encryptionService.decrypt(any())).thenAnswer {
      val arg = it.arguments[0] as String
      arg.removePrefix("enc(").removeSuffix(")")
    }
  }

  private fun sampleEntity(name: String = "my-s3"): CredentialEntity {
    val encryptedData = objectMapper.writeValueAsString(
      mapOf("accessKey" to "${cipherPrefix}enc(AKIAIOSFODNN7)", "secretKey" to "${cipherPrefix}enc(wJalrXUtnFEMI)")
    )
    return CredentialEntity(
      credentialId = UUID.randomUUID(), userId = userId, name = name,
      type = "s3", typeVersion = "1.0.0", data = JsonValue(encryptedData),
      description = "Test", createdBy = userId, updatedBy = userId,
      createdAt = Instant.now(), updatedAt = Instant.now()
    )
  }

  @Test
  fun `createCredential validates data against schema and succeeds`() {
    whenever(credentialTypeRepository.findByTypeAndVersion("s3", "1.0.0")).thenReturn(s3TypeEntity())
    whenever(credentialRepository.existsByUserIdAndName(userId, "my-s3")).thenReturn(false)
    whenever(credentialRepository.save(any())).thenAnswer { it.arguments[0] as CredentialEntity }

    val response = credentialService.createCredential(userId, CredentialCreateRequest(
      name = "my-s3", type = "s3", typeVersion = "1.0.0",
      data = mapOf("accessKey" to "AKIA...", "secretKey" to "wJal...")
    ))

    assertEquals("s3", response.type)
    assertEquals("1.0.0", response.typeVersion)
  }

  @Test
  fun `createCredential throws when data fails schema validation`() {
    whenever(credentialTypeRepository.findByTypeAndVersion("s3", "1.0.0")).thenReturn(s3TypeEntity())

    assertThrows(CredentialDataValidationException::class.java) {
      credentialService.createCredential(userId, CredentialCreateRequest(
        name = "my-s3", type = "s3", typeVersion = "1.0.0",
        data = mapOf("accessKey" to "AKIA...")  // missing required "secretKey"
      ))
    }
    verify(credentialRepository, never()).save(any())
  }

  @Test
  fun `createCredential skips validation when schema is empty`() {
    whenever(credentialTypeRepository.findByTypeAndVersion("generic", "1.0.0")).thenReturn(emptySchemaTypeEntity())
    whenever(credentialRepository.existsByUserIdAndName(userId, "my-cred")).thenReturn(false)
    whenever(credentialRepository.save(any())).thenAnswer { it.arguments[0] as CredentialEntity }

    val response = credentialService.createCredential(userId, CredentialCreateRequest(
      name = "my-cred", type = "generic", typeVersion = "1.0.0",
      data = mapOf("anything" to "goes")
    ))

    assertNotNull(response)
  }

  @Test
  fun `createCredential throws when type+version does not exist`() {
    whenever(credentialTypeRepository.findByTypeAndVersion("s3", "9.0.0")).thenReturn(null)

    assertThrows(CredentialTypeNotFoundException::class.java) {
      credentialService.createCredential(userId, CredentialCreateRequest(
        name = "x", type = "s3", typeVersion = "9.0.0", data = mapOf("k" to "v")
      ))
    }
  }

  @Test
  fun `createCredential throws when name already exists`() {
    whenever(credentialTypeRepository.findByTypeAndVersion("s3", "1.0.0")).thenReturn(s3TypeEntity())
    whenever(credentialRepository.existsByUserIdAndName(userId, "my-s3")).thenReturn(true)

    assertThrows(CredentialAlreadyExistsException::class.java) {
      credentialService.createCredential(userId, CredentialCreateRequest(
        name = "my-s3", type = "s3", typeVersion = "1.0.0",
        data = mapOf("accessKey" to "a", "secretKey" to "b")
      ))
    }
  }

  @Test
  fun `updateCredential validates new data against schema`() {
    whenever(credentialRepository.findByUserIdAndName(userId, "my-s3")).thenReturn(sampleEntity())
    whenever(credentialTypeRepository.findByTypeAndVersion("s3", "1.0.0")).thenReturn(s3TypeEntity())

    assertThrows(CredentialDataValidationException::class.java) {
      credentialService.updateCredential(userId, "my-s3", CredentialUpdateRequest(
        data = mapOf("accessKey" to "a")  // missing required "secretKey"
      ))
    }
  }

  @Test
  fun `updateCredential validates data against new type version schema`() {
    val v2Schema = """{"type":"object","properties":{"token":{"type":"string"}},"required":["token"]}"""
    val v2Type = CredentialTypeEntity(
      credentialTypeId = UUID.randomUUID(), type = "s3",
      schema = JsonValue(v2Schema), uiSchema = JsonValue("{}"),
      credentialTypeVersion = "2.0.0"
    )
    whenever(credentialRepository.findByUserIdAndName(userId, "my-s3")).thenReturn(sampleEntity())
    whenever(credentialTypeRepository.findByTypeAndVersion("s3", "2.0.0")).thenReturn(v2Type)
    whenever(credentialRepository.save(any())).thenAnswer { it.arguments[0] as CredentialEntity }

    val response = credentialService.updateCredential(userId, "my-s3", CredentialUpdateRequest(
      typeVersion = "2.0.0", data = mapOf("token" to "abc123")
    ))

    assertEquals("2.0.0", response.typeVersion)
  }

  @Test
  fun `getCredential returns decrypted data`() {
    whenever(credentialRepository.findByUserIdAndName(userId, "my-s3")).thenReturn(sampleEntity())
    whenever(credentialRepository.save(any())).thenAnswer { it.arguments[0] as CredentialEntity }

    val response = credentialService.getCredential(userId, "my-s3")

    assertEquals("AKIAIOSFODNN7", response.data["accessKey"])
  }

  @Test
  fun `deleteCredential throws when not found`() {
    whenever(credentialRepository.existsByUserIdAndName(userId, "x")).thenReturn(false)

    assertThrows(CredentialNotFoundException::class.java) {
      credentialService.deleteCredential(userId, "x")
    }
  }
}
