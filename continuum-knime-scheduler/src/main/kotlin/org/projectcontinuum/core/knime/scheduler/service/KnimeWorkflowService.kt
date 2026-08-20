package org.projectcontinuum.core.knime.scheduler.service

import org.projectcontinuum.core.knime.scheduler.entity.KnimeWorkflowEntity
import org.projectcontinuum.core.knime.scheduler.exception.InvalidWorkflowFileException
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowNotFoundException
import org.projectcontinuum.core.knime.scheduler.model.KnimeWorkflowResponse
import org.projectcontinuum.core.knime.scheduler.repository.KnimeWorkflowRepository
import org.projectcontinuum.core.knime.scheduler.util.KnimeWorkflowKeyBuilder
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import java.time.Instant
import java.util.UUID

@Service
class KnimeWorkflowService(
  private val repository: KnimeWorkflowRepository,
  private val objectStorageService: ObjectStorageService
) {

  fun create(file: MultipartFile, ownedBy: String): KnimeWorkflowResponse {
    validateKnwfFile(file)
    val workflowId = UUID.randomUUID()
    val objectKey = KnimeWorkflowKeyBuilder.build(ownedBy, workflowId)

    file.inputStream.use { objectStorageService.putObject(objectKey, it, file.size, file.contentType) }

    val entity = KnimeWorkflowEntity(
      workflowId = workflowId,
      ownedBy = ownedBy,
      fileName = file.originalFilename ?: "$workflowId.knwf",
      objectKey = objectKey,
      bucketName = objectStorageService.bucketName,
      sizeBytes = file.size,
      contentType = file.contentType
    )
    return repository.save(entity).toResponse()
  }

  fun list(ownedBy: String, pageable: Pageable): Page<KnimeWorkflowResponse> =
    repository.findAllByOwnedBy(ownedBy, pageable).map { it.toResponse() }

  fun get(workflowId: UUID, ownedBy: String): KnimeWorkflowResponse =
    findOwnedOrThrow(workflowId, ownedBy).toResponse()

  fun download(workflowId: UUID, ownedBy: String): Pair<KnimeWorkflowEntity, ResponseInputStream<GetObjectResponse>> {
    val entity = findOwnedOrThrow(workflowId, ownedBy)
    return entity to objectStorageService.getObject(entity.objectKey)
  }

  fun replace(workflowId: UUID, ownedBy: String, file: MultipartFile): KnimeWorkflowResponse {
    validateKnwfFile(file)
    val entity = findOwnedOrThrow(workflowId, ownedBy)

    file.inputStream.use { objectStorageService.putObject(entity.objectKey, it, file.size, file.contentType) }

    entity.fileName = file.originalFilename ?: entity.fileName
    entity.sizeBytes = file.size
    entity.contentType = file.contentType
    entity.updatedAt = Instant.now()
    return repository.save(entity).toResponse()
  }

  fun delete(workflowId: UUID, ownedBy: String) {
    val entity = findOwnedOrThrow(workflowId, ownedBy)
    objectStorageService.deleteObject(entity.objectKey)
    repository.delete(entity)
  }

  private fun findOwnedOrThrow(workflowId: UUID, ownedBy: String): KnimeWorkflowEntity =
    repository.findByWorkflowIdAndOwnedBy(workflowId, ownedBy)
      ?: throw KnimeWorkflowNotFoundException(workflowId)

  private fun validateKnwfFile(file: MultipartFile) {
    val name = file.originalFilename
    if (file.isEmpty || name.isNullOrBlank() || !KnimeWorkflowKeyBuilder.hasKnwfExtension(name)) {
      throw InvalidWorkflowFileException("Uploaded file must be a non-empty .knwf file")
    }
  }

  private fun KnimeWorkflowEntity.toResponse() = KnimeWorkflowResponse(
    workflowId = workflowId,
    fileName = fileName,
    sizeBytes = sizeBytes,
    contentType = contentType,
    createdAt = createdAt,
    updatedAt = updatedAt
  )
}
