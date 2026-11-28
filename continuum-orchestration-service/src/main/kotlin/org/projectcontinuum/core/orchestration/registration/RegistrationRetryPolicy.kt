package org.projectcontinuum.core.orchestration.registration

import java.time.Duration

/**
 * How often a built-in node registration is tried at start-up: [maxAttempts] sends in total, waiting [initialDelay]
 * before the second one and [multiplier] times longer before each further one, never more than [maxDelay]. The default
 * is 5 attempts with waits of 5, 10, 20 and 40 seconds, about 75 seconds of waiting plus the send time: enough to ride
 * out a Kafka or schema registry that starts a little after the orchestrator. Code constants, not configuration (the
 * service keeps its configuration to the Temporal block).
 */
data class RegistrationRetryPolicy(
  val maxAttempts: Int = 5,
  val initialDelay: Duration = Duration.ofSeconds(5),
  val multiplier: Double = 2.0,
  val maxDelay: Duration = Duration.ofSeconds(60)
) {

  init {
    require(maxAttempts >= 1) { "maxAttempts must be at least 1, got $maxAttempts" }
    require(multiplier >= 1.0) { "multiplier must be at least 1, got $multiplier" }
  }

  /** The wait before attempt [attemptNumber] (1-based): zero for the first attempt. */
  fun delayBeforeAttempt(attemptNumber: Int): Duration {
    require(attemptNumber >= 1) { "attempts are counted from 1, got $attemptNumber" }
    if (attemptNumber == 1) return Duration.ZERO
    val growth = Math.pow(multiplier, (attemptNumber - 2).toDouble())
    val delayMillis = (initialDelay.toMillis() * growth).toLong()
    return Duration.ofMillis(minOf(delayMillis, maxDelay.toMillis()))
  }
}
