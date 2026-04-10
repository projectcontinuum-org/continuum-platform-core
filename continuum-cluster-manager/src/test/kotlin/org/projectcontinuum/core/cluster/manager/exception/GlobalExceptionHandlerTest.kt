package org.projectcontinuum.core.cluster.manager.exception

import io.fabric8.kubernetes.client.KubernetesClientException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingRequestHeaderException

class GlobalExceptionHandlerTest {

  private val handler = GlobalExceptionHandler()

  @Test
  fun `handleNotFound returns 404 with error message`() {
    val ex = WorkbenchNotFoundException("Workbench 'test' not found for user 'user-1'")
    val response = handler.handleNotFound(ex)

    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    assertEquals("Workbench 'test' not found for user 'user-1'", response.body!!["error"])
  }

  @Test
  fun `handleKubernetesError returns 500 with K8s error details`() {
    val ex = KubernetesClientException("connection refused")
    val response = handler.handleKubernetesError(ex)

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    assertEquals("Kubernetes operation failed: connection refused", response.body!!["error"])
  }

  @Test
  fun `handleBadRequest returns 400 with error message`() {
    val ex = IllegalArgumentException("Workbench 'wb' already exists for user 'user-1'")
    val response = handler.handleBadRequest(ex)

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertEquals("Workbench 'wb' already exists for user 'user-1'", response.body!!["error"])
  }

  @Test
  fun `handleBadRequest returns default message when exception message is null`() {
    val ex = IllegalArgumentException()
    val response = handler.handleBadRequest(ex)

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertEquals("Bad request", response.body!!["error"])
  }

  @Test
  fun `handleMissingHeader returns 400 with header name`() {
    val method = String::class.java.getMethod("toString")
    val coreParam = org.springframework.core.MethodParameter(method, -1)
    val ex = MissingRequestHeaderException("x-continuum-user-id", coreParam)
    val response = handler.handleMissingHeader(ex)

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertTrue(response.body!!["error"]!!.contains("x-continuum-user-id"))
  }

  @Test
  fun `handleGenericError returns 500 with generic message`() {
    val ex = RuntimeException("something went wrong")
    val response = handler.handleGenericError(ex)

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    assertEquals("Internal server error", response.body!!["error"])
  }

  @Test
  fun `handleGenericError does not leak exception details`() {
    val ex = RuntimeException("sensitive database connection string: jdbc://...")
    val response = handler.handleGenericError(ex)

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    // Should NOT contain the sensitive message
    assertEquals("Internal server error", response.body!!["error"])
  }
}
