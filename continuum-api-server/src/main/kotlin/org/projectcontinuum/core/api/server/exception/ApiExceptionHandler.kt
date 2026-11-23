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

  /**
   * The Temporal SDK wraps every schedule failure (create, describe, pause, trigger, delete, ...) in a
   * [ScheduleException], so only its gRPC cause says whose fault it is. `INVALID_ARGUMENT` means Temporal rejected
   * what the client sent (an invalid cron expression or time zone): 400. Everything else (Temporal unreachable, a
   * timeout, a schedule that exists in the database but not in Temporal) is a 500, not a client error.
   * [org.projectcontinuum.core.api.server.service.WorkflowScheduleService] turns a missing Temporal schedule into a
   * [WorkflowScheduleNotFoundException] where that is a 404.
   */
  @ExceptionHandler(ScheduleException::class)
  fun handleScheduleFailure(e: ScheduleException): ProblemDetail {
    if (TemporalErrors.grpcStatusCode(e) == Status.Code.INVALID_ARGUMENT) {
      LOGGER.info("Temporal rejected a schedule: {}", e.message)
      return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        "The schedule was rejected by Temporal; check cronExpression and timeZone"
      )
    }
    return handleUnexpected(e)
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
   * A bare Temporal gRPC failure (not wrapped in a [ScheduleException]). `INVALID_ARGUMENT` means Temporal rejected
   * what the client sent: 400 with Temporal's reason. Every other Temporal status stays a 500.
   */
  @ExceptionHandler(StatusRuntimeException::class)
  fun handleTemporalStatus(e: StatusRuntimeException): ProblemDetail {
    if (e.status.code == Status.Code.INVALID_ARGUMENT) {
      val reason = e.status.description ?: "no reason given"
      return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Rejected by Temporal: $reason")
    }
    return handleUnexpected(e)
  }

  @ExceptionHandler(Exception::class)
  fun handleUnexpected(e: Exception): ProblemDetail {
    LOGGER.error("Unhandled exception", e)
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
  }
}
