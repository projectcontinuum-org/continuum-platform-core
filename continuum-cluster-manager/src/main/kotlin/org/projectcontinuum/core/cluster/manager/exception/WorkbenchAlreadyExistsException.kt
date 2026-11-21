package org.projectcontinuum.core.cluster.manager.exception

/** The caller already has an active (running or suspended) workbench with this name. Mapped to 409. */
class WorkbenchAlreadyExistsException(message: String) : RuntimeException(message)
