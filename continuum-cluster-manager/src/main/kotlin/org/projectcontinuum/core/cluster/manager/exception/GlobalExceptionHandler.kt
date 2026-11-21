package org.projectcontinuum.core.cluster.manager.exception

import io.fabric8.kubernetes.client.KubernetesClientException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientException
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Every error of the cluster-manager API is an RFC 9457 [ProblemDetail] (`application/problem+json`), the error
 * contract of all Continuum services. Each body also carries the message in the legacy `error` property (deprecated),
 * because the embedded UI and existing clients read `error` from the former `{"error": "..."}` bodies.
 */
@RestControllerAdvice(basePackages = ["org.projectcontinuum.core.cluster.manager.controller"])
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

  private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

  companion object {
    /** Deprecated: the same text as `detail`, kept for clients of the former `{"error": "..."}` bodies. */
    const val LEGACY_ERROR_PROPERTY = "error"

    fun problem(status: HttpStatus, detail: String): ProblemDetail =
      ProblemDetail.forStatusAndDetail(status, detail).also { it.setProperty(LEGACY_ERROR_PROPERTY, detail) }
  }

  @ExceptionHandler(WorkbenchNotFoundException::class)
  fun handleNotFound(ex: WorkbenchNotFoundException): ResponseEntity<ProblemDetail> =
    respond(problem(status = HttpStatus.NOT_FOUND, detail = ex.message ?: "Not found"))

  @ExceptionHandler(WorkbenchAlreadyExistsException::class)
  fun handleConflict(ex: WorkbenchAlreadyExistsException): ResponseEntity<ProblemDetail> =
    respond(problem(status = HttpStatus.CONFLICT, detail = ex.message ?: "Workbench already exists"))

  @ExceptionHandler(KubernetesClientException::class)
  fun handleKubernetesError(ex: KubernetesClientException): ResponseEntity<ProblemDetail> {
    log.error("Kubernetes client error", ex)
    return respond(problem(status = HttpStatus.INTERNAL_SERVER_ERROR, detail = "Kubernetes operation failed: ${ex.message}"))
  }

  @ExceptionHandler(IllegalArgumentException::class)
  fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> =
    respond(problem(status = HttpStatus.BAD_REQUEST, detail = ex.message ?: "Bad request"))

  @ExceptionHandler(RestClientException::class)
  fun handleRestClientError(ex: RestClientException): ResponseEntity<ProblemDetail> {
    log.error("External API call failed", ex)
    return respond(problem(status = HttpStatus.BAD_GATEWAY, detail = "Failed to fetch data from external service"))
  }

  @ExceptionHandler(Exception::class)
  fun handleGenericError(ex: Exception): ResponseEntity<ProblemDetail> {
    log.error("Unexpected error", ex)
    return respond(problem(status = HttpStatus.INTERNAL_SERVER_ERROR, detail = "Internal server error"))
  }

  /** A missing required header (e.g. `X-Forwarded-Host` on `/authorize`) is a 400, with the header named in `detail`. */
  override fun handleServletRequestBindingException(
    ex: org.springframework.web.bind.ServletRequestBindingException,
    headers: HttpHeaders,
    status: HttpStatusCode,
    request: WebRequest
  ): ResponseEntity<Any>? {
    val detail = if (ex is MissingRequestHeaderException) ex.message else ex.body.detail ?: "Bad request"
    return ResponseEntity.status(status).headers(headers).body(problem(status = HttpStatus.valueOf(status.value()), detail = detail))
  }

  /** Spring MVC's own errors (malformed body, bad parameter type, 405, 415, ...) also get the legacy `error` property. */
  override fun createResponseEntity(
    body: Any?,
    headers: HttpHeaders,
    statusCode: HttpStatusCode,
    request: WebRequest
  ): ResponseEntity<Any> {
    if (body is ProblemDetail && body.properties?.containsKey(LEGACY_ERROR_PROPERTY) != true) {
      body.setProperty(LEGACY_ERROR_PROPERTY, body.detail ?: body.title ?: "Bad request")
    }
    return super.createResponseEntity(body, headers, statusCode, request)
  }

  private fun respond(problem: ProblemDetail): ResponseEntity<ProblemDetail> =
    ResponseEntity.status(problem.status).body(problem)
}
