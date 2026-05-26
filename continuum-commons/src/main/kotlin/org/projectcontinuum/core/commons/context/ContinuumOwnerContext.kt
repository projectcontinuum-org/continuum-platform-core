package org.projectcontinuum.core.commons.context

/**
 * Thread-local holder for the owner (user) ID during workflow and activity execution.
 *
 * This context is set by the API server before starting a Temporal workflow and propagated
 * to activities via [ContinuumContextPropagator]. It allows the worker to know which user
 * owns the current workflow execution, enabling per-user operations like credential resolution.
 *
 * Usage:
 * - API server: `ContinuumOwnerContext.set(ownerId)` before `WorkflowClient.start()`
 * - Worker activity: `ContinuumOwnerContext.get()` to read the propagated owner ID
 * - Always call `clear()` in a `finally` block to prevent thread-local leaks
 */
object ContinuumOwnerContext {

  /** Header key used for Temporal context propagation */
  const val HEADER_KEY = "x-continuum-owner-id"

  private val threadLocal = ThreadLocal<String?>()

  /** Sets the owner ID for the current thread */
  fun set(ownerId: String) {
    threadLocal.set(ownerId)
  }

  /** Returns the owner ID for the current thread, or null if not set */
  fun get(): String? = threadLocal.get()

  /** Clears the owner ID from the current thread to prevent leaks */
  fun clear() {
    threadLocal.remove()
  }
}
