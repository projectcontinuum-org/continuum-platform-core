package org.projectcontinuum.core.knime.scheduler.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

  data class ErrorResponse(val status: Int, val message: String?)

  @ExceptionHandler(KnimeWorkflowNotFoundException::class)
  fun handleNotFound(ex: KnimeWorkflowNotFoundException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.NOT_FOUND)
      .body(ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.message))

  @ExceptionHandler(InvalidWorkflowFileException::class)
  fun handleInvalidFile(ex: InvalidWorkflowFileException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.message))
}
