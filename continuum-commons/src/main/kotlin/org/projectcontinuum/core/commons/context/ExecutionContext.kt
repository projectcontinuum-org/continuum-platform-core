package org.projectcontinuum.core.commons.context

import org.projectcontinuum.core.commons.exception.CredentialsNotFoundException
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.Collections

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
 * Not a `data class`, but it keeps the members the 0.0.22 data class had, with the same meaning: [copy], [equals] /
 * [hashCode] over the constructor values, [toString] and [component1]. A copy goes through the constructor, so its
 * inherited variables are copied and unmodifiable again. Like a data class copy, it does not carry the output flow
 * variables written so far, and those are not part of equality.
 *
 * @param ownerId The user/tenant who owns this workflow execution (from x-continuum-user-id header)
 * @param credentialFetcher Fetches a credential's key-value data by name, on demand.
 * @param inputFlowVariables Flow variables collected from this node's ancestors. Copied into an unmodifiable map, so
 *   later changes to the passed map are not seen and the node cannot change it.
 */
class ExecutionContext(
  val ownerId: String? = null,
  private val credentialFetcher: CredentialFetcher = CredentialFetcher { null },
  inputFlowVariables: Map<String, Any> = emptyMap()
) {

  /** The flow variables inherited from this node's ancestors: an unmodifiable copy, in the order received. */
  val inputFlowVariables: Map<String, Any> = Collections.unmodifiableMap(LinkedHashMap(inputFlowVariables))

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

  // --------------------------------------------------------------------------
  // Members of the former data class, kept for source and binary compatibility
  // --------------------------------------------------------------------------

  /**
   * Returns a new context with the given values. Goes through the constructor, so [inputFlowVariables] is copied into
   * an unmodifiable map; the output flow variables set so far are not carried over.
   */
  fun copy(
    ownerId: String? = this.ownerId,
    credentialFetcher: CredentialFetcher = this.credentialFetcher,
    inputFlowVariables: Map<String, Any> = this.inputFlowVariables
  ): ExecutionContext = ExecutionContext(
    ownerId = ownerId,
    credentialFetcher = credentialFetcher,
    inputFlowVariables = inputFlowVariables
  )

  /** The two-argument `copy` of the 0.0.22 data class, kept for binary compatibility only; Kotlin callers get [copy]. */
  @Deprecated(message = "Binary compatibility with continuum-commons 0.0.22", level = DeprecationLevel.HIDDEN)
  fun copy(
    ownerId: String? = this.ownerId,
    credentialFetcher: CredentialFetcher = this.credentialFetcher
  ): ExecutionContext = copy(ownerId = ownerId, credentialFetcher = credentialFetcher, inputFlowVariables = inputFlowVariables)

  /** [ownerId], for destructuring (`val (ownerId) = executionContext`), as the data class allowed. */
  operator fun component1(): String? = ownerId

  /** Equal when [ownerId], the credential fetcher and [inputFlowVariables] are equal, as for the data class. */
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ExecutionContext) return false
    return ownerId == other.ownerId &&
      credentialFetcher == other.credentialFetcher &&
      inputFlowVariables == other.inputFlowVariables
  }

  override fun hashCode(): Int {
    var result = ownerId?.hashCode() ?: 0
    result = 31 * result + credentialFetcher.hashCode()
    result = 31 * result + inputFlowVariables.hashCode()
    return result
  }

  override fun toString(): String =
    "ExecutionContext(ownerId=$ownerId, credentialFetcher=$credentialFetcher, inputFlowVariables=$inputFlowVariables)"

  companion object {
    /**
     * An empty context with no owner, no credential fetcher and no flow variables. A new instance on every access,
     * so flow variables written by one node never leak into another.
     */
    val EMPTY: ExecutionContext get() = ExecutionContext()

    private val objectMapper = jacksonObjectMapper()
  }
}
