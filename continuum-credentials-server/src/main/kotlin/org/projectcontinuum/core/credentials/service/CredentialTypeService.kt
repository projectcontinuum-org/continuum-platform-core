package org.projectcontinuum.core.credentials.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.projectcontinuum.core.credentials.entity.CredentialTypeEntity
import org.projectcontinuum.core.credentials.entity.JsonValue
import org.projectcontinuum.core.credentials.exception.CredentialAlreadyExistsException
import org.projectcontinuum.core.credentials.exception.CredentialTypeNotFoundException
import org.projectcontinuum.core.credentials.model.CredentialTypeCreateRequest
import org.projectcontinuum.core.credentials.model.CredentialTypeResponse
import org.projectcontinuum.core.credentials.repository.CredentialTypeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class CredentialTypeService(
  private val credentialTypeRepository: CredentialTypeRepository,
  private val objectMapper: ObjectMapper
) {

  private val logger = LoggerFactory.getLogger(CredentialTypeService::class.java)
  private val mapTypeRef = object : TypeReference<Map<String, Any?>>() {}

  companion object {
    private val CREDENTIAL_TYPE_NAMESPACE = UUID.fromString("a3f1e8b0-7c2d-4e5f-9a1b-3d6c8f0e2b4a")
  }

  fun createType(request: CredentialTypeCreateRequest): CredentialTypeResponse {
    if (credentialTypeRepository.existsByTypeAndVersion(request.type, request.version)) {
      throw CredentialAlreadyExistsException(
        "Credential type '${request.type}' version '${request.version}' already exists"
      )
    }

    val normalizedSchema = normalizeJson(request.schema)
    val normalizedUiSchema = normalizeJson(request.uiSchema)
    val typeId = generateTypeId(request.type, normalizedSchema, normalizedUiSchema)

    if (credentialTypeRepository.existsById(typeId)) {
      throw CredentialAlreadyExistsException(
        "A credential type '${request.type}' with the same schema already exists under a different version"
      )
    }

    val now = Instant.now()
    val entity = CredentialTypeEntity(
      credentialTypeId = typeId,
      type = request.type,
      schema = JsonValue(normalizedSchema),
      uiSchema = JsonValue(normalizedUiSchema),
      credentialTypeVersion = request.version,
      createdAt = now,
      updatedAt = now
    )

    val saved = credentialTypeRepository.save(entity)
    logger.info("Created credential type '{}' v{}", saved.type, saved.credentialTypeVersion)
    return toResponse(saved)
  }

  fun getType(type: String, version: String): CredentialTypeResponse {
    val entity = credentialTypeRepository.findByTypeAndVersion(type, version)
      ?: throw CredentialTypeNotFoundException("Credential type '$type' version '$version' not found")
    return toResponse(entity)
  }

  fun getTypeVersions(type: String): List<CredentialTypeResponse> {
    val entities = credentialTypeRepository.findAllByType(type)
    if (entities.isEmpty()) {
      throw CredentialTypeNotFoundException("Credential type '$type' not found")
    }
    return entities.map { toResponse(it) }
  }

  fun listTypes(): List<CredentialTypeResponse> {
    return credentialTypeRepository.findAll().map { toResponse(it) }
  }

  private fun normalizeJson(data: Map<String, Any?>): String {
    val tree = objectMapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(data)
    return objectMapper.writeValueAsString(tree)
  }

  private fun generateTypeId(type: String, schema: String, uiSchema: String): UUID {
    val name = "$type:$schema:$uiSchema"
    return uuidV5(CREDENTIAL_TYPE_NAMESPACE, name)
  }

  private fun uuidV5(namespace: UUID, name: String): UUID {
    val sha1 = MessageDigest.getInstance("SHA-1")
    sha1.update(toBytes(namespace))
    sha1.update(name.toByteArray(Charsets.UTF_8))
    val hash = sha1.digest()

    hash[6] = (hash[6].toInt() and 0x0F or 0x50).toByte()
    hash[8] = (hash[8].toInt() and 0x3F or 0x80).toByte()

    val buffer = ByteBuffer.wrap(hash)
    return UUID(buffer.long, buffer.long)
  }

  private fun toBytes(uuid: UUID): ByteArray {
    val buffer = ByteBuffer.allocate(16)
    buffer.putLong(uuid.mostSignificantBits)
    buffer.putLong(uuid.leastSignificantBits)
    return buffer.array()
  }

  private fun toResponse(entity: CredentialTypeEntity): CredentialTypeResponse {
    return CredentialTypeResponse(
      type = entity.type,
      schema = objectMapper.readValue(entity.schema.value, mapTypeRef),
      uiSchema = objectMapper.readValue(entity.uiSchema.value, mapTypeRef),
      version = entity.credentialTypeVersion,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt
    )
  }
}
