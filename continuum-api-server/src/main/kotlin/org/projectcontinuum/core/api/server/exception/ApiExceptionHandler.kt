package org.projectcontinuum.core.api.server.exception

import cz.jirutka.rsql.parser.RSQLParserException
import io.github.perplexhub.rsql.ConversionException
import io.github.perplexhub.rsql.RSQLException
import io.github.perplexhub.rsql.UnknownPropertyException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.temporal.client.WorkflowNotFoundException
import io.temporal.client.schedules.ScheduleException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * The single error contract of the API server: every error is an RFC 9457 [ProblemDetail]
 * (`application/problem+json`). [ResponseEntityExceptionHandler] covers Spring MVC's own errors (missing or
 * unparsable parameters, malformed bodies, 404 for unknown paths, 405, 415). Anything unexpected is a 500 with a
 * generic detail: exception messages and stack traces never reach the client.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
  }

  @ExceptionHandler(WorkflowValidationException::class)
  fun handleInvalidWorkflow(e: WorkflowValidationException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Invalid workflow").also {
      it.title = "Invalid workflow"
      it.setProperty("errors", e.errors)
      it.setProperty("warnings", e.warnings)
    }

  @ExceptionHandler(InvalidRequestException::class)
  fun handleInvalidRequest(e: InvalidRequestException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Invalid request")

  @ExceptionHandler(ResourceNotFoundException::class)
  fun handleNotFound(e: ResourceNotFoundException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.message ?: "Not found")

  /** Unknown workflow, or one the caller does not own: the same 404, so ids of other tenants cannot be probed. */
  @ExceptionHandler(WorkflowNotFoundException::class)
  fun handleWorkflowNotFound(e: WorkflowNotFoundException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Workflow not found")

  /** Unknown schedule, or one the caller does not own: the same 404. The owner is not echoed back. */
  @ExceptionHandler(WorkflowScheduleNotFoundException::class)
  fun handleScheduleNotFound(e: WorkflowScheduleNotFoundException): ProblemDetail =
    ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Schedule not found")

  /** Temporal rejected the schedule (for example an invalid cron expression or time zone). */
  @ExceptionHandler(ScheduleException::class)
  fun handleScheduleRejected(e: ScheduleException): ProblemDetail {
    LOGGER.info("Temporal rejected a schedule: {}", e.message)
    return ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "The schedule was rejected by Temporal; check cronExpression and timeZone"
    )
  }

  /**
   * A malformed RSQL `filter` or `sort`: unparsable syntax ([RSQLParserException]), an unknown field
   * ([UnknownPropertyException]) or a value of the wrong type ([ConversionException]), all [RSQLException]s.
   */
  @ExceptionHandler(RSQLParserException::class, RSQLException::class)
  fun handleInvalidRsql(e: RuntimeException): ProblemDetail {
    val reason = when (e) {
      is RSQLParserException -> "it is not valid RSQL syntax"
      is UnknownPropertyException -> "it names a field that cannot be filtered on"
      is ConversionException -> "a value has the wrong type for its field (dates are ISO-8601, e.g. 2026-11-01T00:00:00Z)"
      else -> "it cannot be applied"
    }
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid filter: $reason").also {
      it.title = "Invalid filter"
    }
  }

  /**
   * Temporal rejected a visibility query that passed [org.projectcontinuum.core.api.server.utils.TemporalVisibilityQuery]
   * (for example an unknown search attribute or a type mismatch). That is a client error; every other Temporal status
   * stays a 500.
   */
  @ExceptionHandler(StatusRuntimeException::class)
  fun handleTemporalStatus(e: StatusRuntimeException): ProblemDetail {
    if (e.status.code == Status.Code.INVALID_ARGUMENT) {
      return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "Invalid visibility query: ${e.status.description ?: "rejected by Temporal"}"
      )
    }
    return handleUnexpected(e)
  }

  @ExceptionHandler(Exception::class)
  fun handleUnexpected(e: Exception): ProblemDetail {
    LOGGER.error("Unhandled exception", e)
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
  }
}
