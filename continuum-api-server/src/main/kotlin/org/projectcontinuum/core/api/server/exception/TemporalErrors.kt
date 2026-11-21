package org.projectcontinuum.core.api.server.exception

import io.grpc.Status
import io.grpc.StatusRuntimeException

/** Reads the gRPC status behind a Temporal SDK exception, which wraps the [StatusRuntimeException] as a cause. */
object TemporalErrors {

  /** The gRPC status code of the first [StatusRuntimeException] in the cause chain of [failure], or null. */
  fun grpcStatusCode(failure: Throwable): Status.Code? =
    generateSequence(failure) { cause -> cause.cause }
      .filterIsInstance<StatusRuntimeException>()
      .firstOrNull()
      ?.status
      ?.code
}
