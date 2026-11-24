package org.projectcontinuum.core.commons.context

import org.projectcontinuum.core.commons.exception.CredentialsNotFoundException
import tools.jackson.core.JacksonException
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
 * ## Flow variables
 * Small key/value pairs passed down the workflow alongside the data ports.
 * - [inputFlowVariables] (read-only) holds every variable set by this node's ancestors, as collected by the
 *   orchestrator. Read them with [getFlowVariable].
 * - [setFlowVariable] / [setFlowVariables] record this node's own variables. Only these are returned to the
 *   orchestrator (see [getOutputFlowVariables]); downstream nodes see them together with the inherited ones, and a
 *   value set here overrides an inherited value of the same name.
 * - Values are never null and should be JSON-friendly (strings, numbers, booleans, lists, maps): they travel as JSON,
 *   so a number may come back as a different numeric type. Use the typed [getFlowVariable] overloads to convert.
 * - The serialized size of a node's own variables is capped by the worker
 *   (`continuum.core.worker.flow-variables.max-size-bytes`, default 64 KiB).
 *
 * ```kotlin
 * val batch = executionContext.getFlowVariable<Int>("batchNumber") ?: 0
 * executionContext.setFlowVariable(name = "batchNumber", value = batch + 1)
 * ```
 *
 * `copy()` does not carry the output variables written so far.
 *
 * @param ownerId The user/tenant who owns this workflow execution (from x-continuum-user-id header)
 * @param credentialFetcher Fetches a credential's key-value data by name, on demand.
 * @param inputFlowVariables Flow variables collected from this node's ancestors (read-only).
 */
data class ExecutionContext(
  val ownerId: String? = null,
  private val credentialFetcher: CredentialFetcher = CredentialFetcher { null },
  val inputFlowVariables: Map<String, Any> = emptyMap()
) {

  /** This node's own flow variables, in the order they were first set. */
  private val outputFlowVariables = LinkedHashMap<String, Any>()

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

  /** Returns the inherited flow variable [name] as received, or null when no ancestor set it. */
  fun getFlowVariable(name: String): Any? = inputFlowVariables[name]

  /**
   * Returns the inherited flow variable [name] converted to [type] (Jackson `convertValue`), or null when no ancestor
   * set it.
   *
   * @throws IllegalArgumentException if the value cannot be converted to [type]
   */
  fun <T : Any> getFlowVariable(name: String, type: Class<T>): T? {
    val value = inputFlowVariables[name] ?: return null
    return try {
      objectMapper.convertValue(value, type)
    } catch (e: JacksonException) {
      throw IllegalArgumentException("Flow variable '$name' cannot be converted to ${type.name}: ${e.originalMessage}", e)
    }
  }

  /**
   * Reified convenience overload — `executionContext.getFlowVariable<Int>("batchNumber")`. Pass the type argument
   * explicitly; without it the untyped [getFlowVariable] is chosen.
   */
  @JvmName("getFlowVariableReified")
  inline fun <reified T : Any> getFlowVariable(name: String): T? = getFlowVariable(name, T::class.java)

  /**
   * Sets this node's own flow variable [name] to [value], replacing a value set earlier by this node. Passed to all
   * downstream nodes, overriding an inherited variable of the same name.
   *
   * @throws IllegalArgumentException if [name] is blank
   */
  fun setFlowVariable(name: String, value: Any) {
    require(name.isNotBlank()) { "Flow variable name must not be blank" }
    outputFlowVariables[name] = value
  }

  /** Sets each entry of [variables] as with [setFlowVariable]. */
  fun setFlowVariables(variables: Map<String, Any>) {
    variables.forEach { (name, value) -> setFlowVariable(name = name, value = value) }
  }

  /** Returns a copy of the flow variables this node set itself (inherited variables are not included). */
  fun getOutputFlowVariables(): Map<String, Any> = LinkedHashMap(outputFlowVariables)

  companion object {
    /**
     * An empty context with no owner, no credential fetcher and no flow variables. A new instance on every access,
     * so flow variables written by one node never leak into another.
     */
    val EMPTY: ExecutionContext get() = ExecutionContext()

    private val objectMapper = jacksonObjectMapper()
  }
}
