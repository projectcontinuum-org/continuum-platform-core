package org.projectcontinuum.core.commons.context

/**
 * Execution context passed to a node's `execute()` method.
 *
 * Contains runtime information that the framework resolves before node execution,
 * such as the owner identity and pre-fetched credentials. Node developers access
 * this by overriding the `execute()` overload that accepts an `ExecutionContext`.
 *
 * ## Example usage inside a node's execute() method:
 * ```kotlin
 * override fun execute(
 *     properties: Map<String, Any>?,
 *     inputs: Map<String, NodeInputReader>,
 *     nodeOutputWriter: NodeOutputWriter,
 *     nodeProgressCallback: NodeProgressCallback,
 *     executionContext: ExecutionContext
 * ) {
 *     val awsCreds = executionContext.getCredential("AWS Credentials")
 *         ?: throw NodeRuntimeException(workflowId = "", nodeId = "", message = "AWS credentials not found")
 *
 *     val accessKey = awsCreds["accessKeyId"] as String
 *     val secretKey = awsCreds["secretAccessKey"] as String
 *
 *     // Use credentials to connect to AWS, etc.
 * }
 * ```
 *
 * @param ownerId The user/tenant who owns this workflow execution (from x-continuum-user-id header)
 * @param credentials Resolved credentials map: label -> credential data (key-value pairs).
 *   The label comes from `options.credentialLabel` in the UI Schema (or falls back to the property name).
 */
data class ExecutionContext(
  val ownerId: String? = null,
  val credentials: Map<String, Map<String, Any>> = emptyMap()
) {

  /**
   * Returns the credential data for the given label, or null if not found.
   *
   * @param label The credential label as defined in the UI Schema's `options.credentialLabel`,
   *   or the property name if no label was specified
   * @return The credential's key-value data map, or null
   */
  fun getCredential(label: String): Map<String, Any>? = credentials[label]

  companion object {
    /** An empty context with no owner and no credentials */
    val EMPTY = ExecutionContext()
  }
}
