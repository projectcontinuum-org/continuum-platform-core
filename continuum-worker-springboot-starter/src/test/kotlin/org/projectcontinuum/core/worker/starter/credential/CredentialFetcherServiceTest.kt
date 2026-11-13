package org.projectcontinuum.core.worker.starter.credential

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.net.URI
import java.net.URLDecoder

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
      restTemplate.exchange(any<URI>(), eq(HttpMethod.GET), any<HttpEntity<Void>>(), eq(String::class.java))
    ).thenAnswer { invocation ->
      val url = invocation.getArgument<URI>(0)
      val name = URLDecoder.decode(url.rawPath.substringAfterLast("/"), Charsets.UTF_8)
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
      restTemplate.exchange(any<URI>(), eq(HttpMethod.GET), any<HttpEntity<Void>>(), eq(String::class.java))
    ).thenAnswer { ResponseEntity.ok<String>(null) }

    val result = service.fetch("anything", "owner-1")

    assertNull(result)
  }

  @Test
  fun `returns null when the response body is malformed`() {
    whenever(
      restTemplate.exchange(any<URI>(), eq(HttpMethod.GET), any<HttpEntity<Void>>(), eq(String::class.java))
    ).thenAnswer { ResponseEntity.ok("not-json") }

    val result = service.fetch("anything", "owner-1")

    assertNull(result)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "../admin",
      "../../api/v1/users",
      "a/b",
      "a?owner=other#f",
      "a b",
      "a\nb",
      "100%",
      "\$(id)",
      "x'); SELECT 1; --",
      "{name}",
    ]
  )
  fun `hostile names stay inside a single encoded path segment`(hostile: String) {
    credentialStore[hostile] = mapOf("k" to "v")

    val result = service.fetch(hostile, "owner-1")

    val captor = argumentCaptor<URI>()
    verify(restTemplate).exchange(captor.capture(), eq(HttpMethod.GET), any<HttpEntity<Void>>(), eq(String::class.java))
    val uri = captor.firstValue
    assertEquals("localhost", uri.host)
    assertEquals(8083, uri.port)
    assertNull(uri.rawQuery)
    assertNull(uri.rawFragment)
    assertTrue(uri.rawPath.startsWith("/api/v1/credentials/"))
    val segment = uri.rawPath.removePrefix("/api/v1/credentials/")
    assertFalse(segment.contains("/"), "name must be a single segment: ${uri.rawPath}")
    assertEquals(hostile, URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8))
    assertEquals(mapOf("k" to "v"), result)
  }

  @Test
  fun `traversal name is percent-encoded`() {
    assertEquals("/api/v1/credentials/..%2Fadmin", service.credentialUri("../admin").rawPath)
  }

  @ParameterizedTest
  @ValueSource(strings = ["", " ", ".", ".."])
  fun `dot-segment and blank names are refused without a request`(name: String) {
    assertNull(service.fetch(name, "owner-1"))
    verify(restTemplate, never()).exchange(any<URI>(), any<HttpMethod>(), any<HttpEntity<Void>>(), eq(String::class.java))
  }
}
