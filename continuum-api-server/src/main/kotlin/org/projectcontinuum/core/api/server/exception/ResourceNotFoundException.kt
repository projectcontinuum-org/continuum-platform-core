package org.projectcontinuum.core.api.server.exception

/**
 * The requested resource does not exist **or is not owned by the caller**. Both cases are deliberately
 * indistinguishable so that a tenant cannot probe for other tenants' ids. Mapped to 404 by [ApiExceptionHandler].
 */
class ResourceNotFoundException(message: String) : RuntimeException(message)
