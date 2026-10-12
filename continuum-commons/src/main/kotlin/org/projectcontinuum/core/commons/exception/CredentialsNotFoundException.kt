package org.projectcontinuum.core.commons.exception

/** Thrown by [org.projectcontinuum.core.commons.context.ExecutionContext.getCredential] when no credential exists for the given name. */
class CredentialsNotFoundException(name: String) : RuntimeException("Credential '$name' not found")
