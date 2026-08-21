package org.projectcontinuum.core.knime.scheduler.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.knime.scheduler.entity.KnimeWorkflowEntity
import org.projectcontinuum.core.knime.scheduler.exception.InvalidExecutionStatusException
import org.projectcontinuum.core.knime.scheduler.exception.InvalidWorkflowFileException
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowNotFoundException
import org.projectcontinuum.core.knime.scheduler.repository.KnimeWorkflowExecutionRepository
import org.projectcontinuum.core.knime.scheduler.repository.KnimeWorkflowRepository
import org.springframework.data.domain.PageRequest
import org.springframework.mock.web.MockMultipartFile
import java.util.UUID

class KnimeWorkflowServiceTest {

  private val repository: KnimeWorkflowRepository = mock()
  private val executionRepository: KnimeWorkflowExecutionRepository = mock()
  private val objectStorageService: ObjectStorageService = mock()
  private lateinit var service: KnimeWorkflowService

  @BeforeEach
  fun setUp() {
    whenever(objectStorageService.bucketName).thenReturn("continuum-knime-workflows")
    service = KnimeWorkflowService(repository, executionRepository, objectStorageService)
  }

  @Test
  fun `create rejects files without a knwf extension`() {
    val file = MockMultipartFile("file", "workflow.zip", "application/zip", "content".toByteArray())

    assertThrows(InvalidWorkflowFileException::class.java) {
      service.create(file, "alice")
    }
  }

  @Test
  fun `create rejects empty files`() {
    val file = MockMultipartFile("file", "workflow.knwf", "application/octet-stream", ByteArray(0))

    assertThrows(InvalidWorkflowFileException::class.java) {
      service.create(file, "alice")
    }
  }

  @Test
  fun `create uploads to the owner-scoped key and persists a bookkeeping row`() {
    val file = MockMultipartFile("file", "workflow.knwf", "application/octet-stream", "content".toByteArray())
    whenever(repository.save(any())).thenAnswer { it.arguments[0] }

    val response = service.create(file, "alice")

    assertEquals("workflow.knwf", response.fileName)
    verify(objectStorageService).putObject(
      eq("knime-workflows/users/alice/${response.workflowId}.knwf"),
      any(), any(), any()
    )
  }

  @Test
  fun `get throws not found when the workflow is not owned by the caller`() {
    whenever(repository.findByWorkflowIdAndOwnedBy(any(), any())).thenReturn(null)

    assertThrows(KnimeWorkflowNotFoundException::class.java) {
      service.get(UUID.randomUUID(), "bob")
    }
  }

  @Test
  fun `delete removes the object before removing the bookkeeping row`() {
    val file = MockMultipartFile("file", "workflow.knwf", "application/octet-stream", "content".toByteArray())
    whenever(repository.save(any())).thenAnswer { it.arguments[0] }
    val created = service.create(file, "alice")
    whenever(repository.findByWorkflowIdAndOwnedBy(created.workflowId, "alice"))
      .thenAnswer {
        org.projectcontinuum.core.knime.scheduler.entity.KnimeWorkflowEntity(
          workflowId = created.workflowId,
          ownedBy = "alice",
          fileName = created.fileName,
          objectKey = "knime-workflows/users/alice/${created.workflowId}.knwf",
          bucketName = "continuum-knime-workflows",
          sizeBytes = created.sizeBytes,
          contentType = created.contentType
        )
      }

    service.delete(created.workflowId, "alice")

    verify(objectStorageService).deleteObject("knime-workflows/users/alice/${created.workflowId}.knwf")
  }

  private fun ownedWorkflowEntity(workflowId: UUID) = KnimeWorkflowEntity(
    workflowId = workflowId,
    ownedBy = "alice",
    fileName = "workflow.knwf",
    objectKey = "knime-workflows/users/alice/$workflowId.knwf",
    bucketName = "continuum-knime-workflows",
    sizeBytes = 42L,
    contentType = "application/octet-stream"
  )

  @Test
  fun `uploadExecution throws when the source workflow is not owned by the caller`() {
    val workflowId = UUID.randomUUID()
    whenever(repository.findByWorkflowIdAndOwnedBy(workflowId, "alice")).thenReturn(null)
    val file = MockMultipartFile("file", "result.knwf", "application/octet-stream", "content".toByteArray())

    assertThrows(KnimeWorkflowNotFoundException::class.java) {
      service.uploadExecution(workflowId, "alice", file, "SUCCESS")
    }
  }

  @Test
  fun `uploadExecution rejects an unrecognized status value`() {
    val workflowId = UUID.randomUUID()
    whenever(repository.findByWorkflowIdAndOwnedBy(workflowId, "alice")).thenReturn(ownedWorkflowEntity(workflowId))
    val file = MockMultipartFile("file", "result.knwf", "application/octet-stream", "content".toByteArray())

    assertThrows(InvalidExecutionStatusException::class.java) {
      service.uploadExecution(workflowId, "alice", file, "BOGUS")
    }
  }

  @Test
  fun `uploadExecution stores the result under a per-execution key and persists the reported status`() {
    val workflowId = UUID.randomUUID()
    whenever(repository.findByWorkflowIdAndOwnedBy(workflowId, "alice")).thenReturn(ownedWorkflowEntity(workflowId))
    whenever(executionRepository.save(any())).thenAnswer { it.arguments[0] }
    val file = MockMultipartFile("file", "result.knwf", "application/octet-stream", "content".toByteArray())

    val response = service.uploadExecution(workflowId, "alice", file, "FAILED")

    assertEquals("FAILED", response.status)
    assertEquals(workflowId, response.workflowId)
    verify(objectStorageService).putObject(
      eq("knime-workflows/users/alice/$workflowId/executions/${response.executionId}.knwf"),
      any(), any(), any()
    )
  }

  @Test
  fun `listExecutions throws when the source workflow is not owned by the caller`() {
    val workflowId = UUID.randomUUID()
    whenever(repository.findByWorkflowIdAndOwnedBy(workflowId, "alice")).thenReturn(null)

    assertThrows(KnimeWorkflowNotFoundException::class.java) {
      service.listExecutions(workflowId, "alice", PageRequest.of(0, 10))
    }
  }

  @Test
  fun `downloadExecution throws when the execution is not owned by the caller`() {
    val workflowId = UUID.randomUUID()
    val executionId = UUID.randomUUID()
    whenever(executionRepository.findByExecutionIdAndWorkflowIdAndOwnedBy(executionId, workflowId, "alice"))
      .thenReturn(null)

    assertThrows(KnimeWorkflowNotFoundException::class.java) {
      service.downloadExecution(workflowId, executionId, "alice")
    }
  }
}
