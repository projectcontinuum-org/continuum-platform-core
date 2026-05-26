package org.projectcontinuum.core.credentials.exception

import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["org.projectcontinuum.core.credentials.controller"])
class GlobalExceptionHandler {

  private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

  @ExceptionHandler(CredentialNotFoundException::class)
  fun handleNotFound(ex: CredentialNotFoundException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
      .body(mapOf("error" to (ex.message ?: "Not found")))
  }

  @ExceptionHandler(CredentialTypeNotFoundException::class)
  fun handleTypeNotFound(ex: CredentialTypeNotFoundException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
      .body(mapOf("error" to (ex.message ?: "Credential type not found")))
  }

  @ExceptionHandler(CredentialAlreadyExistsException::class)
  fun handleConflict(ex: CredentialAlreadyExistsException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.CONFLICT)
      .body(mapOf("error" to (ex.message ?: "Credential already exists")))
  }

  @ExceptionHandler(CredentialDataValidationException::class)
  fun handleDataValidation(ex: CredentialDataValidationException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(mapOf("error" to (ex.message ?: "Credential data validation failed")))
  }

  @ExceptionHandler(EncryptionException::class)
  fun handleEncryptionError(ex: EncryptionException): ResponseEntity<Map<String, String>> {
    logger.error("Encryption error", ex)
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(mapOf("error" to "Failed to process credential data"))
  }

  @ExceptionHandler(IllegalArgumentException::class)
  fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(mapOf("error" to (ex.message ?: "Bad request")))
  }

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
    val errors = ex.bindingResult.fieldErrors.joinToString("; ") {
      "${it.field}: ${it.defaultMessage}"
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(mapOf("error" to errors))
  }

  @ExceptionHandler(MissingRequestHeaderException::class)
  fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(mapOf("error" to (ex.message ?: "Missing required header")))
  }

  @ExceptionHandler(OptimisticLockingFailureException::class)
  fun handleOptimisticLock(ex: OptimisticLockingFailureException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.CONFLICT)
      .body(mapOf("error" to "Resource was modified concurrently, please retry"))
  }

  @ExceptionHandler(Exception::class)
  fun handleGenericError(ex: Exception): ResponseEntity<Map<String, String>> {
    logger.error("Unexpected error", ex)
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(mapOf("error" to "Internal server error"))
  }
}
