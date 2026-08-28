package org.projectcontinuum.core.commons.context

import org.projectcontinuum.core.commons.exception.CredentialsNotFoundException
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Fetches a single credential by name on demand.
 *
 * Implementations typically call out to `continuum-credentials-server`. Injected into
 * [ExecutionContext] by the worker framework so a node can fetch exactly the credential(s)
 * it needs, by whatever name it parses from its own `properties` — the framework does not
 * inspect the node's UI Schema to infer which properties reference credentials.
 */
fun interface CredentialFetcher {
  fun fetch(name: String): Map<String, Any>?
}

/**
 * Execution context passed to a node's `execute()` method.
 *
 * Contains runtime information that the framework resolves before node execution,
 * such as the owner identity and a credential fetcher. Node developers access
 * this by overriding the `execute()` overload that accepts an `ExecutionContext`.
 *
 * ## Example usage inside a node's execute() method:
 * ```kotlin
 * data class AwsCredential(val accessKeyId: String, val secretAccessKey: String)
 *
 * override fun execute(
 *     properties: Map<String, Any>?,
 *     inputs: Map<String, NodeInputReader>,
 *     nodeOutputWriter: NodeOutputWriter,
 *     nodeProgressCallback: NodeProgressCallback,
 *     executionContext: ExecutionContext
 * ) {
 *     val credentialName = properties?.get("awsCredential") as? String
 *         ?: throw NodeRuntimeException(workflowId = "", nodeId = "", message = "AWS credential not configured")
 *     val awsCreds = executionContext.getCredential<AwsCredential>(credentialName)
 *
 *     // awsCreds.accessKeyId, awsCreds.secretAccessKey — no manual map casting
 *
 *     // Use credentials to connect to AWS, etc.
 * }
 * ```
 *
 * @param ownerId The user/tenant who owns this workflow execution (from x-continuum-user-id header)
 * @param credentialFetcher Fetches a credential's key-value data by name, on demand.
 */
data class ExecutionContext(
  val ownerId: String? = null,
  private val credentialFetcher: CredentialFetcher = CredentialFetcher { null }
) {

  /**
   * Returns the credential data for the given name deserialized into [T].
   *
   * @param name The credential's name, as stored in `continuum-credentials-server`
   * @param type The type to deserialize the credential's key-value data into
   * @return The deserialized credential
   * @throws CredentialsNotFoundException if no credential exists for [name]
   */
  fun <T> getCredential(name: String, type: Class<T>): T {
    val raw = credentialFetcher.fetch(name) ?: throw CredentialsNotFoundException(name)
    return objectMapper.convertValue(raw, type)
  }

  /** Reified convenience overload of [getCredential] — `executionContext.getCredential<MyCredential>(name)`. */
  inline fun <reified T> getCredential(name: String): T = getCredential(name, T::class.java)

  companion object {
    /** An empty context with no owner and no credential fetcher */
    val EMPTY = ExecutionContext()

    private val objectMapper = jacksonObjectMapper()
  }
}
