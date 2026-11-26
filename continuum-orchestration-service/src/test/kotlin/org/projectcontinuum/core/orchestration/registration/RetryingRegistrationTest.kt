package org.projectcontinuum.core.orchestration.registration

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The built-in registration is retried with backoff until it is sent, at most five times. */
class RetryingRegistrationTest {

  /** Fails the first [failures] sends (exceptions, or "not accepted" when [refuse] is set), then succeeds. */
  private class FlakySender(private val failures: Int, private val refuse: Boolean = false) : RegistrationSender {
    val workerIds = mutableListOf<String>()
    val registeredAts = mutableListOf<Instant>()

    override fun send(definition: BuiltinNodeDefinition, workerId: String, registeredAt: Instant): RegistrationSendResult {
      workerIds += workerId
      registeredAts += registeredAt
      if (workerIds.size > failures) return RegistrationSendResult.Sent
      return if (refuse) RegistrationSendResult.NotAccepted else RegistrationSendResult.Failed(exception = IllegalStateException("schema registry unreachable"))
    }
  }

  private fun capturingLogger(): Pair<Logger, ListAppender<ILoggingEvent>> {
    val logger = LoggerFactory.getLogger("retrying-registration-test-${System.nanoTime()}") as Logger
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    logger.addAppender(appender)
    return logger to appender
  }

  private fun registration(sender: RegistrationSender, scheduler: RetryScheduler, logger: Logger) = RetryingRegistration(
    definition = BuiltinNodes.loop,
    workerId = "orchestration-fixed",
    registeredAt = Instant.EPOCH,
    sender = sender,
    scheduler = scheduler,
    logger = logger
  )

  @Test
  fun `a registration that fails a few times succeeds on the next attempt and stops there`() {
    listOf(0, 1, 2, 4).forEach { failures ->
      val sender = FlakySender(failures = failures)
      val scheduler = VirtualTimeScheduler()
      val (logger, _) = capturingLogger()

      registration(sender = sender, scheduler = scheduler, logger = logger).start()
      scheduler.runAll()

      assertEquals(failures + 1, sender.workerIds.size, "attempts after $failures failures")
      assertEquals(0, scheduler.pendingCount, "nothing is sent again after a success")
    }
  }

  @Test
  fun `a registration that always fails is tried five times and then logged once as an error`() {
    listOf(false, true).forEach { refuse ->
      val sender = FlakySender(failures = Int.MAX_VALUE, refuse = refuse)
      val scheduler = VirtualTimeScheduler()
      val (logger, appender) = capturingLogger()

      registration(sender = sender, scheduler = scheduler, logger = logger).start()
      scheduler.runAll()

      assertEquals(5, sender.workerIds.size)
      val errors = appender.list.filter { event -> event.level == Level.ERROR }
      val warnings = appender.list.filter { event -> event.level == Level.WARN }
      assertEquals(1, errors.size)
      assertEquals(4, warnings.size)
      val errorText = errors.single().formattedMessage
      assertTrue(errorText.contains("org.projectcontinuum.core.builtin.Loop") && errorText.contains("5 attempts"), errorText)
    }
  }

  @Test
  fun `the waits follow the policy and add up to about 75 seconds`() {
    val sender = FlakySender(failures = Int.MAX_VALUE)
    val scheduler = VirtualTimeScheduler()
    val (logger, _) = capturingLogger()

    registration(sender = sender, scheduler = scheduler, logger = logger).start()
    scheduler.runAll()

    assertEquals(listOf(0L, 5L, 10L, 20L, 40L), scheduler.requestedDelays.map { delay -> delay.toSeconds() })
  }

  @Test
  fun `every attempt reuses the worker id and registration time of the start`() {
    val sender = FlakySender(failures = 3)
    val scheduler = VirtualTimeScheduler()
    val (logger, _) = capturingLogger()

    registration(sender = sender, scheduler = scheduler, logger = logger).start()
    scheduler.runAll()

    assertEquals(setOf("orchestration-fixed"), sender.workerIds.toSet())
    assertEquals(setOf(Instant.EPOCH), sender.registeredAts.toSet())
  }

  @Test
  fun `shutting down stops pending retries`() {
    val sender = FlakySender(failures = Int.MAX_VALUE)
    val scheduler = VirtualTimeScheduler()
    val (logger, _) = capturingLogger()

    registration(sender = sender, scheduler = scheduler, logger = logger).start()
    scheduler.advanceBy(duration = Duration.ofSeconds(6))
    scheduler.shutDown()
    scheduler.runAll()

    assertEquals(2, sender.workerIds.size)
  }

  @Test
  fun `the policy caps a long wait at the maximum delay`() {
    val policy = RegistrationRetryPolicy(maxAttempts = 10)

    assertEquals(Duration.ZERO, policy.delayBeforeAttempt(attemptNumber = 1))
    assertEquals(Duration.ofSeconds(40), policy.delayBeforeAttempt(attemptNumber = 5))
    assertEquals(Duration.ofSeconds(60), policy.delayBeforeAttempt(attemptNumber = 6))
    assertEquals(Duration.ofSeconds(60), policy.delayBeforeAttempt(attemptNumber = 10))
  }
}
