package org.projectcontinuum.core.credentials.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.projectcontinuum.core.credentials.entity.CredentialEntity
import org.projectcontinuum.core.credentials.entity.JsonValue
import org.projectcontinuum.core.credentials.exception.CredentialAlreadyExistsException
import org.projectcontinuum.core.credentials.exception.CredentialDataValidationException
import org.projectcontinuum.core.credentials.exception.CredentialNotFoundException
import org.projectcontinuum.core.credentials.exception.CredentialTypeNotFoundException
import org.projectcontinuum.core.credentials.model.CredentialCreateRequest
import org.projectcontinuum.core.credentials.model.CredentialResponse
import org.projectcontinuum.core.credentials.model.CredentialUpdateRequest
import org.projectcontinuum.core.credentials.repository.CredentialRepository
import org.projectcontinuum.core.credentials.repository.CredentialTypeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class CredentialService(
  private val credentialRepository: CredentialRepository,
  private val credentialTypeRepository: CredentialTypeRepository,
  private val encryptionService: EncryptionService,
  private val objectMapper: ObjectMapper
) {

  private val logger = LoggerFactory.getLogger(CredentialService::class.java)
  private val mapTypeRef = object : TypeReference<Map<String, String>>() {}
  private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4)

  fun createCredential(userId: String, request: CredentialCreateRequest): CredentialResponse {
    val typeEntity = credentialTypeRepository.findByTypeAndVersion(request.type, request.typeVersion)
      ?: throw CredentialTypeNotFoundException(
        "Credential type '${request.type}' version '${request.typeVersion}' does not exist"
      )

    validateDataAgainstSchema(request.data, typeEntity.schema)

    if (credentialRepository.existsByUserIdAndName(userId, request.name)) {
      throw CredentialAlreadyExistsException(
        "Credential '${request.name}' already exists for user '$userId'"
      )
    }

    val now = Instant.now()
    val entity = CredentialEntity(
      credentialId = UUID.randomUUID(),
      userId = userId,
      name = request.name,
      type = request.type,
      typeVersion = request.typeVersion,
      data = encryptMap(request.data),
      description = request.description,
      createdBy = userId,
      updatedBy = userId,
      createdAt = now,
      updatedAt = now
    )

    val saved = credentialRepository.save(entity)
    logger.info("Created credential '{}' for user '{}'", saved.name, userId)
    return toResponse(saved)
  }

  fun listCredentials(userId: String): List<CredentialResponse> {
    val entities = credentialRepository.findAllByUserId(userId)
    return entities.map { entity ->
      val updated = entity.copy(lastAccessedAt = Instant.now())
      credentialRepository.save(updated)
      toResponse(updated)
    }
  }

  fun getCredential(userId: String, name: String): CredentialResponse {
    val entity = credentialRepository.findByUserIdAndName(userId, name)
      ?: throw CredentialNotFoundException("Credential '$name' not found for user '$userId'")

    val updated = entity.copy(lastAccessedAt = Instant.now())
    credentialRepository.save(updated)
    return toResponse(updated)
  }

  fun getCredentialsByType(userId: String, type: String): List<CredentialResponse> {
    val entities = credentialRepository.findAllByUserIdAndType(userId, type)
    return entities.map { entity ->
      val updated = entity.copy(lastAccessedAt = Instant.now())
      credentialRepository.save(updated)
      toResponse(updated)
    }
  }

  fun getCredentialsByTypeAndVersion(userId: String, type: String, typeVersion: String): List<CredentialResponse> {
    val entities = credentialRepository.findAllByUserIdAndTypeAndTypeVersion(userId, type, typeVersion)
    return entities.map { entity ->
      val updated = entity.copy(lastAccessedAt = Instant.now())
      credentialRepository.save(updated)
      toResponse(updated)
    }
  }

  fun updateCredential(userId: String, name: String, request: CredentialUpdateRequest): CredentialResponse {
    val entity = credentialRepository.findByUserIdAndName(userId, name)
      ?: throw CredentialNotFoundException("Credential '$name' not found for user '$userId'")

    val newType = request.type ?: entity.type
    val newTypeVersion = request.typeVersion ?: entity.typeVersion

    if (request.type != null || request.typeVersion != null || request.data != null) {
      val typeEntity = credentialTypeRepository.findByTypeAndVersion(newType, newTypeVersion)
        ?: throw CredentialTypeNotFoundException(
          "Credential type '$newType' version '$newTypeVersion' does not exist"
        )

      if (request.data != null) {
        validateDataAgainstSchema(request.data, typeEntity.schema)
      }
    }

    if (request.name != null && request.name != entity.name) {
      if (credentialRepository.existsByUserIdAndName(userId, request.name)) {
        throw CredentialAlreadyExistsException(
          "Credential '${request.name}' already exists for user '$userId'"
        )
      }
    }

    val updatedEntity = entity.copy(
      name = request.name ?: entity.name,
      type = newType,
      typeVersion = newTypeVersion,
      data = if (request.data != null) encryptMap(request.data) else entity.data,
      description = request.description ?: entity.description,
      updatedBy = userId,
      updatedAt = Instant.now()
    )

    val saved = credentialRepository.save(updatedEntity)
    logger.info("Updated credential '{}' for user '{}'", saved.name, userId)
    return toResponse(saved)
  }

  fun deleteCredential(userId: String, name: String) {
    if (!credentialRepository.existsByUserIdAndName(userId, name)) {
      throw CredentialNotFoundException("Credential '$name' not found for user '$userId'")
    }
    credentialRepository.deleteByUserIdAndName(userId, name)
    logger.info("Deleted credential '{}' for user '{}'", name, userId)
  }

  private fun validateDataAgainstSchema(data: Map<String, String>, schema: JsonValue) {
    val schemaNode = objectMapper.readTree(schema.value)
    if (schemaNode.isEmpty) return

    val dataNode: JsonNode = objectMapper.valueToTree(data)
    val jsonSchema = schemaFactory.getSchema(schemaNode)
    val errors = jsonSchema.validate(dataNode)

    if (errors.isNotEmpty()) {
      val errorMessages = errors.joinToString("; ") { it.message }
      throw CredentialDataValidationException(
        "Credential data does not match the schema: $errorMessages"
      )
    }
  }

  private fun encryptMap(data: Map<String, String>): JsonValue {
    val encrypted = data.mapValues { (_, value) -> CIPHER_PREFIX + encryptionService.encrypt(value) }
    return JsonValue(objectMapper.writeValueAsString(encrypted))
  }

  private fun decryptMap(data: JsonValue): Map<String, String> {
    val encrypted: Map<String, String> = objectMapper.readValue(data.value, mapTypeRef)
    return encrypted.mapValues { (_, value) ->
      val ciphertext = if (value.startsWith(CIPHER_PREFIX)) value.removePrefix(CIPHER_PREFIX) else value
      encryptionService.decrypt(ciphertext)
    }
  }

  private fun toResponse(entity: CredentialEntity): CredentialResponse {
    return CredentialResponse(
      userId = entity.userId,
      name = entity.name,
      type = entity.type,
      typeVersion = entity.typeVersion,
      data = decryptMap(entity.data),
      description = entity.description,
      createdBy = entity.createdBy,
      updatedBy = entity.updatedBy,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
      lastAccessedAt = entity.lastAccessedAt
    )
  }

  companion object {
    private const val CIPHER_PREFIX = "{cipher}"
  }
}
