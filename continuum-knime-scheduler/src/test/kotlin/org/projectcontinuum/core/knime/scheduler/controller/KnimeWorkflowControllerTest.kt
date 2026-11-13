package org.projectcontinuum.core.knime.scheduler.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.knime.scheduler.entity.KnimeWorkflowEntity
import org.projectcontinuum.core.knime.scheduler.exception.GlobalExceptionHandler
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowNotFoundException
import org.projectcontinuum.core.knime.scheduler.model.KnimeWorkflowResponse
import org.projectcontinuum.core.knime.scheduler.service.KnimeWorkflowService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

class KnimeWorkflowControllerTest {

  private val service: KnimeWorkflowService = mock()
  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(KnimeWorkflowController(service))
      .setControllerAdvice(GlobalExceptionHandler())
      .build()
  }

  @Test
  fun `get returns workflow metadata for the requesting user`() {
    val workflowId = UUID.randomUUID()
    val response = KnimeWorkflowResponse(
      workflowId = workflowId,
      fileName = "workflow.knwf",
      sizeBytes = 42L,
      contentType = "application/octet-stream",
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )
    whenever(service.get(workflowId, "alice")).thenReturn(response)

    mockMvc.perform(get("/api/v1/knime-workflows/$workflowId").header("x-continuum-user-id", "alice"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.fileName").value("workflow.knwf"))
  }

  @Test
  fun `get returns 404 when the workflow belongs to a different user`() {
    val workflowId = UUID.randomUUID()
    whenever(service.get(workflowId, "bob")).thenThrow(KnimeWorkflowNotFoundException(workflowId))

    mockMvc.perform(get("/api/v1/knime-workflows/$workflowId").header("x-continuum-user-id", "bob"))
      .andExpect(status().isNotFound)
  }

  @Test
  fun `upload rejects a request missing the file part`() {
    mockMvc.perform(multipart("/api/v1/knime-workflows").header("x-continuum-user-id", "alice"))
      .andExpect(status().isBadRequest)
  }

  private fun downloadWith(fileName: String): String {
    val workflowId = UUID.randomUUID()
    val bytes = byteArrayOf(1, 2, 3)
    val entity = KnimeWorkflowEntity(
      workflowId = workflowId,
      ownedBy = "alice",
      fileName = fileName,
      objectKey = "knime-workflows/users/alice/$workflowId.knwf",
      bucketName = "bucket",
      sizeBytes = bytes.size.toLong()
    )
    val stream = ResponseInputStream(
      GetObjectResponse.builder().build(),
      AbortableInputStream.create(ByteArrayInputStream(bytes))
    )
    whenever(service.download(workflowId, "alice")).thenReturn(entity to stream)
    return mockMvc.perform(get("/api/v1/knime-workflows/$workflowId/content").header("x-continuum-user-id", "alice"))
      .andExpect(status().isOk)
      .andReturn().response.getHeader(HttpHeaders.CONTENT_DISPOSITION)!!
  }

  @Test
  fun `download content-disposition for a plain file name`() {
    val header = downloadWith("workflow.knwf")
    val parsed = ContentDisposition.parse(header)
    assertTrue(parsed.isAttachment)
    assertEquals("workflow.knwf", parsed.filename)
  }

  @Test
  fun `download content-disposition cannot be broken by a hostile uploaded file name`() {
    val hostile = "evil\"; filename=\"x.exe\"\r\nSet-Cookie: a=b caf\u00e9.knwf"
    val header = downloadWith(hostile)

    assertFalse(header.any { Character.isISOControl(it) }, "header must not contain CR/LF/control chars: $header")
    // The injected `"; filename="x.exe"` stays inside the escaped quoted value / filename* encoding.
    val parsed = ContentDisposition.parse(header)
    assertTrue(parsed.isAttachment)
    assertEquals(StandardCharsets.UTF_8, parsed.charset)
    assertEquals("evil\"; filename=\"x.exe\"__Set-Cookie: a=b caf\u00e9.knwf", parsed.filename)
  }

  @Test
  fun `download content-disposition strips path components`() {
    assertEquals("caf\u00e9.knwf", ContentDisposition.parse(downloadWith("..\\..\\etc/caf\u00e9.knwf")).filename)
  }

  @Test
  fun `attachmentDisposition escapes quotes and encodes non-ASCII`() {
    val header = attachmentDisposition("a\"b\u00fc.knwf")
    assertTrue(header.startsWith("attachment;"))
    assertTrue(header.contains("filename*=UTF-8''"), header)
    assertFalse(header.contains("a\"b"), "raw quote must not appear unescaped: $header")
    assertEquals("a\"b\u00fc.knwf", ContentDisposition.parse(header).filename)
  }

  @Test
  fun `attachmentDisposition falls back when the name is blank after sanitising`() {
    assertEquals("workflow.knwf", ContentDisposition.parse(attachmentDisposition("   ")).filename)
    assertEquals("workflow.knwf", ContentDisposition.parse(attachmentDisposition("dir/")).filename)
  }
}
