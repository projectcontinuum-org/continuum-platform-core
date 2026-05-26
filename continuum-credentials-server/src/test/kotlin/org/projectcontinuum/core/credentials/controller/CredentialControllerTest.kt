package org.projectcontinuum.core.credentials.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.projectcontinuum.core.credentials.exception.CredentialNotFoundException
import org.projectcontinuum.core.credentials.exception.CredentialTypeNotFoundException
import org.projectcontinuum.core.credentials.model.CredentialCreateRequest
import org.projectcontinuum.core.credentials.model.CredentialResponse
import org.projectcontinuum.core.credentials.service.CredentialService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@WebMvcTest(CredentialController::class)
class CredentialControllerTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @MockitoBean
  private lateinit var credentialService: CredentialService

  private val userId = "user-1"
  private val now = Instant.parse("2024-01-01T00:00:00Z")

  private fun sampleResponse(name: String = "my-s3") = CredentialResponse(
    userId = userId, name = name, type = "s3", typeVersion = "1.0.0",
    data = mapOf("accessKey" to "AKIAIOSFODNN7"),
    description = "Test", createdBy = userId, updatedBy = userId,
    createdAt = now, updatedAt = now, lastAccessedAt = now
  )

  @Test
  fun `POST creates credential with typeVersion and returns 201`() {
    val request = CredentialCreateRequest(
      name = "my-s3", type = "s3", typeVersion = "1.0.0",
      data = mapOf("accessKey" to "AKIAIOSFODNN7")
    )
    whenever(credentialService.createCredential(eq(userId), any())).thenReturn(sampleResponse())

    mockMvc.perform(
      post("/api/v1/credentials")
        .header("x-continuum-user-id", userId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
    )
      .andExpect(status().isCreated)
      .andExpect(jsonPath("$.type").value("s3"))
      .andExpect(jsonPath("$.typeVersion").value("1.0.0"))
      .andExpect(jsonPath("$.data.accessKey").value("AKIAIOSFODNN7"))
  }

  @Test
  fun `POST with blank typeVersion returns 400`() {
    val request = mapOf(
      "name" to "x", "type" to "s3", "typeVersion" to "",
      "data" to mapOf("k" to "v")
    )

    mockMvc.perform(
      post("/api/v1/credentials")
        .header("x-continuum-user-id", userId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
    )
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `POST with invalid type+version returns 404`() {
    val request = CredentialCreateRequest(
      name = "x", type = "unknown", typeVersion = "1.0.0", data = mapOf("k" to "v")
    )
    whenever(credentialService.createCredential(eq(userId), any()))
      .thenThrow(CredentialTypeNotFoundException("not found"))

    mockMvc.perform(
      post("/api/v1/credentials")
        .header("x-continuum-user-id", userId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request))
    )
      .andExpect(status().isNotFound)
  }

  @Test
  fun `GET by name returns typeVersion`() {
    whenever(credentialService.getCredential(userId, "my-s3")).thenReturn(sampleResponse())

    mockMvc.perform(
      get("/api/v1/credentials/my-s3").header("x-continuum-user-id", userId)
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.typeVersion").value("1.0.0"))
  }

  @Test
  fun `GET list returns credentials`() {
    whenever(credentialService.listCredentials(userId)).thenReturn(listOf(sampleResponse()))

    mockMvc.perform(
      get("/api/v1/credentials").header("x-continuum-user-id", userId)
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
  }

  @Test
  fun `GET by name returns 404 when not found`() {
    whenever(credentialService.getCredential(userId, "x"))
      .thenThrow(CredentialNotFoundException("not found"))

    mockMvc.perform(
      get("/api/v1/credentials/x").header("x-continuum-user-id", userId)
    )
      .andExpect(status().isNotFound)
  }

  @Test
  fun `DELETE returns 204`() {
    doNothing().whenever(credentialService).deleteCredential(userId, "my-s3")

    mockMvc.perform(
      delete("/api/v1/credentials/my-s3").header("x-continuum-user-id", userId)
    )
      .andExpect(status().isNoContent)
  }

  @Test
  fun `defaults to anonymous user when header is missing`() {
    whenever(credentialService.listCredentials("anonymous")).thenReturn(emptyList())

    mockMvc.perform(get("/api/v1/credentials"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(0))
  }
}
