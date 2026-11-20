package org.projectcontinuum.core.api.server.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

  @ExceptionHandler(InvalidRequestException::class)
  fun handleInvalidRequest(e: InvalidRequestException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Invalid request")

  @ExceptionHandler(WorkflowValidationException::class)
  fun handleInvalidWorkflow(e: WorkflowValidationException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Invalid workflow").also {
      it.title = "Invalid workflow"
      it.setProperty("errors", e.errors)
      it.setProperty("warnings", e.warnings)
    }

  @ExceptionHandler(ResourceNotFoundException::class)
  fun handleNotFound(e: ResourceNotFoundException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "Not found")
}
