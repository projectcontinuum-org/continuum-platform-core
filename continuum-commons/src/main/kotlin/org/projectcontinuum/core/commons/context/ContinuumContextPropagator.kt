package org.projectcontinuum.core.commons.context

import io.temporal.api.common.v1.Payload
import io.temporal.common.context.ContextPropagator
import com.google.protobuf.ByteString

/**
 * Temporal context propagator that carries the owner (user) id and, when present, the id of the
 * schedule that triggered the current execution across workflow and activity boundaries.
 *
 * When a workflow is started, this propagator reads the current [ContinuumOwnerContext] and
 * [ContinuumScheduleContext] and serializes them into Temporal headers. When an activity or
 * workflow executes on a worker, the propagator deserializes them back from the headers and
 * restores them on the executing thread.
 *
 * This enables the worker to know which user owns the workflow (required for per-user
 * operations like credential resolution) and, for schedule-triggered workflows, which schedule
 * produced the run (required for `workflow_runs` lineage).
 *
 * ## Registration
 * Must be registered on every Temporal client/worker that starts or executes Continuum
 * workflows:
 * - API server: `WorkflowClientOptions.newBuilder().setContextPropagators(listOf(ContinuumContextPropagator()))`
 * - Spring Boot Temporal: via `TemporalOptionsCustomizer<WorkflowClientOptions.Builder>` bean
 */
class ContinuumContextPropagator : ContextPropagator {

  /** Snapshot of the propagated context values, opaque to Temporal's propagation machinery. */
  data class PropagatedContext(val ownerId: String?, val scheduleId: String?)

  override fun getName(): String = "ContinuumContextPropagator"

  /**
   * Called on the client/workflow thread to capture the current context.
   */
  override fun getCurrentContext(): Any? {
    return PropagatedContext(ContinuumOwnerContext.get(), ContinuumScheduleContext.get())
  }

  /**
   * Called on the activity/workflow thread to restore the context from a deserialized object.
   * The [context] parameter is the object returned by [deserializeContext].
   */
  override fun setCurrentContext(context: Any?) {
    val propagatedContext = context as? PropagatedContext ?: return
    propagatedContext.ownerId?.takeIf { it.isNotBlank() }?.let { ContinuumOwnerContext.set(it) }
    propagatedContext.scheduleId?.takeIf { it.isNotBlank() }?.let { ContinuumScheduleContext.set(it) }
  }

  /**
   * Serializes the context object into Temporal header payloads.
   * Called by the framework to convert the result of [getCurrentContext] into headers.
   */
  override fun serializeContext(context: Any?): Map<String, Payload> {
    val propagatedContext = context as? PropagatedContext ?: return emptyMap()
    val headers = mutableMapOf<String, Payload>()
    propagatedContext.ownerId?.takeIf { it.isNotBlank() }?.let {
      headers[ContinuumOwnerContext.HEADER_KEY] = Payload.newBuilder().setData(ByteString.copyFromUtf8(it)).build()
    }
    propagatedContext.scheduleId?.takeIf { it.isNotBlank() }?.let {
      headers[ContinuumScheduleContext.HEADER_KEY] = Payload.newBuilder().setData(ByteString.copyFromUtf8(it)).build()
    }
    return headers
  }

  /**
   * Deserializes Temporal header payloads back into the context object.
   * The returned object is passed to [setCurrentContext] on the receiving side.
   */
  override fun deserializeContext(context: Map<String, Payload>): Any? {
    return PropagatedContext(
      ownerId = context[ContinuumOwnerContext.HEADER_KEY]?.data?.toStringUtf8(),
      scheduleId = context[ContinuumScheduleContext.HEADER_KEY]?.data?.toStringUtf8()
    )
  }
}
