package org.projectcontinuum.core.cloud.gateway.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(basePackages = ["org.projectcontinuum.core.cloud.gateway.controller"])
class GlobalExceptionHandler {

  private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

  @ExceptionHandler(WorkbenchNotFoundException::class)
  fun handleNotFound(ex: WorkbenchNotFoundException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
      .body(mapOf("error" to (ex.message ?: "Not found")))
  }

  @ExceptionHandler(IllegalArgumentException::class)
  fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(mapOf("error" to (ex.message ?: "Bad request")))
  }

  @ExceptionHandler(MissingRequestHeaderException::class)
  fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(mapOf("error" to (ex.message ?: "Missing required header")))
  }

  @ExceptionHandler(Exception::class)
  fun handleGenericError(ex: Exception): ResponseEntity<Map<String, String>> {
    logger.error("Unexpected error", ex)
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(mapOf("error" to "Internal server error"))
  }
}
