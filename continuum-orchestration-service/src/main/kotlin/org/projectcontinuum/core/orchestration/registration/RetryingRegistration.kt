package org.projectcontinuum.core.orchestration.registration

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/** Runs a task after a delay. Production: a single background thread; tests: an immediate or virtual-time fake. */
fun interface RetryScheduler {
  fun schedule(delay: Duration, task: () -> Unit)
}

/**
 * Registers one built-in node, retrying with the [policy]'s backoff until a send succeeds or the attempts are used up.
 * Each failed attempt is logged at WARN with the attempt count; the last one at ERROR, naming the node and the number
 * of attempts. Nothing is sent after a success. Every attempt reuses the same worker id and registration time, so the
 * registry sees one registration of this start.
 */
class RetryingRegistration(
  private val definition: BuiltinNodeDefinition,
  private val workerId: String,
  private val registeredAt: Instant,
  private val sender: RegistrationSender,
  private val scheduler: RetryScheduler,
  private val policy: RegistrationRetryPolicy = RegistrationRetryPolicy(),
  private val logger: Logger = LoggerFactory.getLogger(RetryingRegistration::class.java)
) {

  /** Schedules the first attempt and returns at once. */
  fun start() {
    scheduleAttempt(attemptNumber = 1)
  }

  private fun scheduleAttempt(attemptNumber: Int) {
    scheduler.schedule(delay = policy.delayBeforeAttempt(attemptNumber = attemptNumber)) { attempt(attemptNumber = attemptNumber) }
  }

  private fun attempt(attemptNumber: Int) {
    val result = sender.send(definition = definition, workerId = workerId, registeredAt = registeredAt)
    if (result == RegistrationSendResult.Sent) {
      logger.info("Published built-in node registration '{}' (worker '{}', attempt {})", definition.nodeId, workerId, attemptNumber)
      return
    }
    val reason = describe(result = result)
    val failure = (result as? RegistrationSendResult.Failed)?.exception
    if (attemptNumber >= policy.maxAttempts) {
      logger.error(
        "Built-in node registration '{}' failed after {} attempts ({}); the node is missing from the palette until the " +
          "orchestration service is restarted",
        definition.nodeId,
        attemptNumber,
        reason,
        failure
      )
      return
    }
    val nextDelay = policy.delayBeforeAttempt(attemptNumber = attemptNumber + 1)
    logger.warn(
      "Built-in node registration '{}' attempt {} of {} failed ({}); retrying in {} s",
      definition.nodeId,
      attemptNumber,
      policy.maxAttempts,
      reason,
      nextDelay.toSeconds()
    )
    scheduleAttempt(attemptNumber = attemptNumber + 1)
  }

  private fun describe(result: RegistrationSendResult): String = when (result) {
    is RegistrationSendResult.Failed -> result.exception.message ?: result.exception.javaClass.simpleName
    RegistrationSendResult.NotAccepted -> "not accepted by the binder"
    RegistrationSendResult.Sent -> "sent"
  }
}
