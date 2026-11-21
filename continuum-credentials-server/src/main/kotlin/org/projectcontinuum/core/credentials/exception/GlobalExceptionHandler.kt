package org.projectcontinuum.core.credentials.exception

import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Every error of the credentials API is an RFC 9457 [ProblemDetail] (`application/problem+json`), the error contract of
 * all Continuum services. Each body also carries the message in the legacy `error` property (deprecated), because the
 * embedded UI and the workbench's credential picker read `error` from the former `{"error": "..."}` bodies.
 *
 * Exception messages are returned only for the service's own, expected exceptions; anything unexpected (including
 * encryption failures) gets a generic detail, never the exception's message.
 *
 * Not scoped by `basePackages` on purpose: Spring only applies a scoped advice when a controller method was resolved,
 * so 404 (unknown path), 405, 406 and 415 would fall back to Boot's default error body.
 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

  private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

  companion object {
    /** Deprecated: the same text as `detail`, kept for clients of the former `{"error": "..."}` bodies. */
    const val LEGACY_ERROR_PROPERTY = "error"

    /** Bean-validation failures, one `{field, message}` per violated constraint. */
    const val FIELD_ERRORS_PROPERTY = "fieldErrors"

    fun problem(status: HttpStatus, detail: String): ProblemDetail =
      ProblemDetail.forStatusAndDetail(status, detail).also { it.setProperty(LEGACY_ERROR_PROPERTY, detail) }
  }

  @ExceptionHandler(CredentialNotFoundException::class)
  fun handleNotFound(ex: CredentialNotFoundException): ResponseEntity<ProblemDetail> =
    respond(problem(status = HttpStatus.NOT_FOUND, detail = ex.message ?: "Not found"))

  @ExceptionHandler(CredentialTypeNotFoundException::class)
  fun handleTypeNotFound(ex: CredentialTypeNotFoundException): ResponseEntity<ProblemDetail> =
    respond(problem(status = HttpStatus.NOT_FOUND, detail = ex.message ?: "Credential type not found"))

  @ExceptionHandler(CredentialAlreadyExistsException::class)
  fun handleConflict(ex: CredentialAlreadyExistsException): ResponseEntity<ProblemDetail> =
    respond(problem(status = HttpStatus.CONFLICT, detail = ex.message ?: "Credential already exists"))

  @ExceptionHandler(CredentialDataValidationException::class)
  fun handleDataValidation(ex: CredentialDataValidationException): ResponseEntity<ProblemDetail> =
    respond(problem(status = HttpStatus.BAD_REQUEST, detail = ex.message ?: "Credential data validation failed"))

  @ExceptionHandler(EncryptionException::class)
  fun handleEncryptionError(ex: EncryptionException): ResponseEntity<ProblemDetail> {
    log.error("Encryption error", ex)
    return respond(problem(status = HttpStatus.INTERNAL_SERVER_ERROR, detail = "Failed to process credential data"))
  }

  @ExceptionHandler(IllegalArgumentException::class)
  fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> =
    respond(problem(status = HttpStatus.BAD_REQUEST, detail = ex.message ?: "Bad request"))

  @ExceptionHandler(OptimisticLockingFailureException::class)
  fun handleOptimisticLock(ex: OptimisticLockingFailureException): ResponseEntity<ProblemDetail> =
    respond(problem(status = HttpStatus.CONFLICT, detail = "Resource was modified concurrently, please retry"))

  @ExceptionHandler(Exception::class)
  fun handleGenericError(ex: Exception): ResponseEntity<ProblemDetail> {
    log.error("Unexpected error", ex)
    return respond(problem(status = HttpStatus.INTERNAL_SERVER_ERROR, detail = "Internal server error"))
  }

  /**
   * Bean validation of a request body. `detail` (and `error`) keep the former joined text
   * (`"name: Name is required; data: Data must not be empty"`); `fieldErrors` lists each violation.
   */
  override fun handleMethodArgumentNotValid(
    ex: MethodArgumentNotValidException,
    headers: HttpHeaders,
    status: HttpStatusCode,
    request: WebRequest
  ): ResponseEntity<Any>? {
    val fieldErrors = ex.bindingResult.fieldErrors.map { fieldError ->
      mapOf("field" to fieldError.field, "message" to (fieldError.defaultMessage ?: "is invalid"))
    }
    val joinedMessages = fieldErrors.joinToString("; ") { "${it["field"]}: ${it["message"]}" }
    val body = problem(status = HttpStatus.BAD_REQUEST, detail = joinedMessages).also {
      it.title = "Invalid request"
      it.setProperty(FIELD_ERRORS_PROPERTY, fieldErrors)
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(body)
  }

  /** A missing required header is a 400 naming the header, as before. */
  override fun handleServletRequestBindingException(
    ex: ServletRequestBindingException,
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
