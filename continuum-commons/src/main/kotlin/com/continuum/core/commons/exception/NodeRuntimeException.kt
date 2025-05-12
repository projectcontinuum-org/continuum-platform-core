package com.continuum.core.commons.exception

class NodeRuntimeException(
    val isRetriable: Boolean = true,
    val workflowId: String,
    val nodeId: String,
    override val message: String
): RuntimeException()