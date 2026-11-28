package org.projectcontinuum.core.loopcondition

/**
 * Thrown from an evaluation listener to stop a condition that hit a cost limit. cel-java wraps it in the evaluation
 * error, and [CelEvaluationFailures] turns any such cause into LIMIT_EXCEEDED with [limitDescription] as the detail.
 * No stack trace: it is control flow, thrown on the thread that evaluates (in the orchestrator, one of its condition
 * evaluation threads).
 */
internal open class EvaluationLimitExceeded(val limitDescription: String) : RuntimeException(limitDescription, null, false, false)
