package org.projectcontinuum.core.commons.context

import io.temporal.api.common.v1.Payload
import io.temporal.common.context.ContextPropagator
import com.google.protobuf.ByteString

/**
 * Temporal context propagator that carries the owner (user) ID across workflow and activity boundaries.
 *
 * When a workflow is started, this propagator reads the current [ContinuumOwnerContext] and serializes
 * the owner ID into a Temporal header. When an activity executes on a worker, the propagator deserializes
 * the owner ID from the header and restores it into [ContinuumOwnerContext] on the activity thread.
 *
 * This enables the worker to know which user owns the workflow, which is required for
 * per-user operations like credential resolution.
 *
 * ## Registration
 * Must be registered on both the client (API server) and the worker (orchestration service, worker starter):
 * - API server: `WorkflowClientOptions.newBuilder().setContextPropagators(listOf(ContinuumContextPropagator()))`
 * - Spring Boot Temporal: via `TemporalOptionsCustomizer<WorkflowClientOptions.Builder>` bean
 */
class ContinuumContextPropagator : ContextPropagator {

  override fun getName(): String = "ContinuumContextPropagator"

  /**
   * Called on the client/workflow thread to capture the current context.
   * Returns the owner ID string (or null) as the context object.
   */
  override fun getCurrentContext(): Any? {
    return ContinuumOwnerContext.get()
  }

  /**
   * Called on the activity/workflow thread to restore the context from a deserialized object.
   * The [context] parameter is the object returned by [deserializeContext].
   */
  override fun setCurrentContext(context: Any?) {
    val ownerId = context as? String
    if (!ownerId.isNullOrBlank()) {
      ContinuumOwnerContext.set(ownerId)
    }
  }

  /**
   * Serializes the context object (owner ID string) into Temporal header payloads.
   * Called by the framework to convert the result of [getCurrentContext] into headers.
   */
  override fun serializeContext(context: Any?): Map<String, Payload> {
    val ownerId = context as? String ?: return emptyMap()
    if (ownerId.isBlank()) return emptyMap()
    val payload = Payload.newBuilder()
      .setData(ByteString.copyFromUtf8(ownerId))
      .build()
    return mapOf(ContinuumOwnerContext.HEADER_KEY to payload)
  }

  /**
   * Deserializes Temporal header payloads back into the context object (owner ID string).
   * The returned object is passed to [setCurrentContext] on the receiving side.
   */
  override fun deserializeContext(context: Map<String, Payload>): Any? {
    val payload = context[ContinuumOwnerContext.HEADER_KEY] ?: return null
    return payload.data.toStringUtf8()
  }
}
