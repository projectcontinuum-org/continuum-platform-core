package org.projectcontinuum.core.knime.scheduler.controller

import org.projectcontinuum.core.knime.scheduler.model.KnimeWorkflowResponse
import org.projectcontinuum.core.knime.scheduler.service.KnimeWorkflowService
import org.springframework.core.io.InputStreamResource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

private const val USER_ID_HEADER = "x-continuum-user-id"

@RestController
@RequestMapping("/api/v1/knime-workflows")
class KnimeWorkflowController(
  private val knimeWorkflowService: KnimeWorkflowService
) {

  @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun upload(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @RequestParam("file") file: MultipartFile
  ): ResponseEntity<KnimeWorkflowResponse> {
    val response = knimeWorkflowService.create(file, userId)
    return ResponseEntity.status(201).body(response)
  }

  @GetMapping
  fun list(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    pageable: Pageable
  ): Page<KnimeWorkflowResponse> = knimeWorkflowService.list(userId, pageable)

  @GetMapping("/{workflowId}")
  fun get(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @PathVariable workflowId: UUID
  ): KnimeWorkflowResponse = knimeWorkflowService.get(workflowId, userId)

  @GetMapping("/{workflowId}/content")
  fun downloadContent(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @PathVariable workflowId: UUID
  ): ResponseEntity<InputStreamResource> {
    val (entity, objectStream) = knimeWorkflowService.download(workflowId, userId)
    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${entity.fileName}\"")
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .contentLength(entity.sizeBytes)
      .body(InputStreamResource(objectStream))
  }

  @PutMapping("/{workflowId}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  fun replace(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @PathVariable workflowId: UUID,
    @RequestParam("file") file: MultipartFile
  ): KnimeWorkflowResponse = knimeWorkflowService.replace(workflowId, userId, file)

  @DeleteMapping("/{workflowId}")
  fun delete(
    @RequestHeader(USER_ID_HEADER, required = false, defaultValue = "anonymous") userId: String,
    @PathVariable workflowId: UUID
  ): ResponseEntity<Void> {
    knimeWorkflowService.delete(workflowId, userId)
    return ResponseEntity.noContent().build()
  }
}
