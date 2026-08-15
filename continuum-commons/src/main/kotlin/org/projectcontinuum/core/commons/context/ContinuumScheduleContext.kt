package org.projectcontinuum.core.commons.context

/**
 * Thread-local holder for the id of the [io.temporal.client.schedules.Schedule] that triggered
 * the current workflow execution, if any.
 *
 * This context is set by the API server before creating a Temporal Schedule and propagated
 * to the fired workflow execution via [ContinuumContextPropagator]. It allows the workflow to
 * report which schedule produced a given run, enabling lineage from `workflow_runs` back to
 * `workflow_schedules`.
 *
 * Usage:
 * - API server: baked directly into the [ScheduleActionStartWorkflow][io.temporal.client.schedules.ScheduleActionStartWorkflow] header at schedule-creation time
 * - Workflow: [get] to read the propagated schedule id when reporting status
 * - Manually started workflows never set this, so [get] returns `null` for them
 */
object ContinuumScheduleContext {

  /** Header key used for Temporal context propagation */
  const val HEADER_KEY = "x-continuum-schedule-id"

  private val threadLocal = ThreadLocal<String?>()

  /** Sets the schedule id for the current thread */
  fun set(scheduleId: String) {
    threadLocal.set(scheduleId)
  }

  /** Returns the schedule id for the current thread, or null if not set */
  fun get(): String? = threadLocal.get()

  /** Clears the schedule id from the current thread to prevent leaks */
  fun clear() {
    threadLocal.remove()
  }
}
