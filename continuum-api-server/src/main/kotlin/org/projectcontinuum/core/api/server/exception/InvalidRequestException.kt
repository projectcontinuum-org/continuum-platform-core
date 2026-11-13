package org.projectcontinuum.core.api.server.exception

/**
 * Thrown when client-supplied input (path variable, query parameter, visibility query) fails
 * server-side validation. Mapped to HTTP 400 by [ApiExceptionHandler].
 */
class InvalidRequestException(message: String) : RuntimeException(message)
