package org.projectcontinuum.core.orchestration.registration

import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Registers the built-in nodes ([BuiltinNodes.all], today only the Loop) in the node registry when the orchestration
 * service has started: one `FeatureRegistrationRequest` per node on the feature registration topic, keyed by node id,
 * exactly like a feature worker's `FeatureRegistrationPublisher`. The message bridge stores it, so the node appears in
 * the palette, node search, the node tree and the documentation endpoint.
 *
 * The sends run on a background thread ([RetryingRegistration], [RegistrationRetryPolicy]: 5 attempts over about two
 * minutes), so start-up never waits for Kafka or the schema registry, and workflows run meanwhile. One worker id per
 * start is shared by every attempt. Shutting the service down stops pending retries.
 */
@Component
class BuiltinNodeRegistrationPublisher internal constructor(
  private val sender: RegistrationSender,
  private val scheduler: RetryScheduler,
  private val retryPolicy: RegistrationRetryPolicy,
  private val shutdownAction: () -> Unit
) {

  /** Production wiring: the real sender and one daemon thread named `builtin-node-registration`. */
  @Autowired
  constructor(streamBridge: StreamBridge) : this(
    sender = BuiltinNodeRegistrationSender(streamBridge = streamBridge),
    executor = newRegistrationExecutor()
  )

  private constructor(sender: RegistrationSender, executor: ScheduledExecutorService) : this(
    sender = sender,
    scheduler = executorScheduler(executor = executor),
    retryPolicy = RegistrationRetryPolicy(),
    shutdownAction = { executor.shutdownNow() }
  )

  private companion object {
    fun newRegistrationExecutor(): ScheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "builtin-node-registration").apply { isDaemon = true }
      }

    fun executorScheduler(executor: ScheduledExecutorService) = RetryScheduler { delay, task ->
      if (!executor.isShutdown) {
        executor.schedule({ task() }, delay.toMillis(), TimeUnit.MILLISECONDS)
      }
    }
  }

  @EventListener(ApplicationReadyEvent::class)
  fun publishBuiltinNodeRegistrations() {
    publish(definitions = BuiltinNodes.all, workerId = "orchestration-${UUID.randomUUID()}", registeredAt = Instant.now())
  }

  /** Starts one retrying registration per definition and returns at once. */
  internal fun publish(definitions: List<BuiltinNodeDefinition>, workerId: String, registeredAt: Instant) {
    definitions.forEach { definition ->
      RetryingRegistration(
        definition = definition,
        workerId = workerId,
        registeredAt = registeredAt,
        sender = sender,
        scheduler = scheduler,
        policy = retryPolicy
      ).start()
    }
  }

  /** Stops pending retries when the service shuts down. */
  @PreDestroy
  fun stopRetries() {
    shutdownAction()
  }
}
