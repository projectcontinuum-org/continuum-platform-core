package org.projectcontinuum.core.credentials.exception

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.credentials.controller.CredentialController
import org.projectcontinuum.core.credentials.service.CredentialService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Every error of the credentials API is `application/problem+json` and keeps the legacy `error` property, which the
 * embedded UI and the workbench's credential picker read.
 */
@WebMvcTest(CredentialController::class)
class ProblemDetailContractTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var credentialService: CredentialService

  @Test
  fun `not found is a 404 problem with the legacy error text`() {
    whenever(credentialService.getCredential(eq("alice"), eq("missing")))
      .thenThrow(CredentialNotFoundException("Credential 'missing' not found for user 'alice'"))

    mockMvc.perform(get("/api/v1/credentials/missing").header("x-continuum-user-id", "alice"))
      .andExpect(status().isNotFound)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(404))
      .andExpect(jsonPath("$.detail").value("Credential 'missing' not found for user 'alice'"))
      .andExpect(jsonPath("$.error").value("Credential 'missing' not found for user 'alice'"))
  }

  @Test
  fun `a duplicate is a 409 problem`() {
    whenever(credentialService.createCredential(eq("alice"), any()))
      .thenThrow(CredentialAlreadyExistsException("Credential 'aws-prod' already exists for user 'alice'"))

    mockMvc.perform(
      post("/api/v1/credentials")
        .header("x-continuum-user-id", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"name":"aws-prod","type":"GENERIC","typeVersion":"1.0.0","data":{"k":"v"}}""")
    )
      .andExpect(status().isConflict)
      .andExpect(jsonPath("$.error").value(containsString("already exists")))
  }

  @Test
  fun `bean validation keeps the joined message and lists each field`() {
    mockMvc.perform(
      post("/api/v1/credentials")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"name":"bad name!","type":"GENERIC","typeVersion":"1.0.0","data":{"k":"v"}}""")
    )
      .andExpect(status().isBadRequest)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("name: Name must contain only alphanumeric characters, hyphens, and underscores"))
      .andExpect(jsonPath("$.error").value("name: Name must contain only alphanumeric characters, hyphens, and underscores"))
      .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
  }

  @Test
  fun `an encryption failure does not leak its message`() {
    whenever(credentialService.listCredentials(eq("alice"))).thenThrow(EncryptionException("bad tag for key 7f3a"))

    mockMvc.perform(get("/api/v1/credentials").header("x-continuum-user-id", "alice"))
      .andExpect(status().isInternalServerError)
      .andExpect(jsonPath("$.detail").value("Failed to process credential data"))
      .andExpect(jsonPath("$.error").value("Failed to process credential data"))
  }

  @Test
  fun `an unexpected failure is a 500 problem with a generic detail`() {
    whenever(credentialService.listCredentials(eq("alice"))).thenThrow(IllegalStateException("jdbc url with password"))

    mockMvc.perform(get("/api/v1/credentials").header("x-continuum-user-id", "alice"))
      .andExpect(status().isInternalServerError)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.detail").value("Internal server error"))
  }

  @Test
  fun `a create without a name is a 400 problem, not a 500`() {
    mockMvc.perform(
      post("/api/v1/credentials")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"type":"GENERIC","typeVersion":"1.0.0","data":{"a":"b"}}""")
    )
      .andExpect(status().isBadRequest)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.error").value("Failed to read request"))
  }

  @Test
  fun `a create with a non-JSON body is a 400 problem, not a 500`() {
    mockMvc.perform(post("/api/v1/credentials").contentType(MediaType.APPLICATION_JSON).content("garbage"))
      .andExpect(status().isBadRequest)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
  }
}
