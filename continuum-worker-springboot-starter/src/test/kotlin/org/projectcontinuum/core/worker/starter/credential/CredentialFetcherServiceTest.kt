package org.projectcontinuum.core.worker.starter.credential

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

class CredentialFetcherServiceTest {

  private val objectMapper = ObjectMapper()
  private val restTemplate: RestTemplate = mock()
  private lateinit var service: CredentialFetcherService
  private val credentialStore = mutableMapOf<String, Map<String, Any>>()

  @BeforeEach
  fun setUp() {
    credentialStore.clear()
    service = CredentialFetcherService(restTemplate, "http://localhost:8083")

    whenever(
      restTemplate.exchange(any<String>(), eq(HttpMethod.GET), any<HttpEntity<Void>>(), eq(String::class.java))
    ).thenAnswer { invocation ->
      val url = invocation.getArgument<String>(0)
      val name = url.substringAfterLast("/")
      val data = credentialStore[name] ?: throw ResourceAccessException("not found: $name")
      ResponseEntity.ok(objectMapper.writeValueAsString(mapOf("data" to data)))
    }
  }

  @Test
  fun `fetches a credential's data by name`() {
    credentialStore["my-aws-credential"] = mapOf("accessKeyId" to "AKIA", "secretAccessKey" to "shh")

    val result = service.fetch("my-aws-credential", "owner-1")

    assertEquals(mapOf("accessKeyId" to "AKIA", "secretAccessKey" to "shh"), result)
  }

  @Test
  fun `returns null when the credential name is not found`() {
    val result = service.fetch("does-not-exist", "owner-1")

    assertNull(result)
  }

  @Test
  fun `returns null when the server returns an empty body`() {
    whenever(
      restTemplate.exchange(any<String>(), eq(HttpMethod.GET), any<HttpEntity<Void>>(), eq(String::class.java))
    ).thenAnswer { ResponseEntity.ok<String>(null) }

    val result = service.fetch("anything", "owner-1")

    assertNull(result)
  }

  @Test
  fun `returns null when the response body is malformed`() {
    whenever(
      restTemplate.exchange(any<String>(), eq(HttpMethod.GET), any<HttpEntity<Void>>(), eq(String::class.java))
    ).thenAnswer { ResponseEntity.ok("not-json") }

    val result = service.fetch("anything", "owner-1")

    assertNull(result)
  }
}
