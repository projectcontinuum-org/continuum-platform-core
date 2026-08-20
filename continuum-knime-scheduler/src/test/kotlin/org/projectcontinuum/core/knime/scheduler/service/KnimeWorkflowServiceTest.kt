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
import org.projectcontinuum.core.knime.scheduler.exception.InvalidWorkflowFileException
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowNotFoundException
import org.projectcontinuum.core.knime.scheduler.repository.KnimeWorkflowRepository
import org.springframework.mock.web.MockMultipartFile
import java.util.UUID

class KnimeWorkflowServiceTest {

  private val repository: KnimeWorkflowRepository = mock()
  private val objectStorageService: ObjectStorageService = mock()
  private lateinit var service: KnimeWorkflowService

  @BeforeEach
  fun setUp() {
    whenever(objectStorageService.bucketName).thenReturn("continuum-knime-workflows")
    service = KnimeWorkflowService(repository, objectStorageService)
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
}
