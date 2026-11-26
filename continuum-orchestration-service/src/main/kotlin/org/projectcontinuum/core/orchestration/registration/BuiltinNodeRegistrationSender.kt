package org.projectcontinuum.core.orchestration.registration

import org.projectcontinuum.core.commons.event.Channels
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import java.time.Instant

/** The result of one registration send. */
sealed interface RegistrationSendResult {
  data object Sent : RegistrationSendResult

  /** The binder refused the message without an exception. */
  data object NotAccepted : RegistrationSendResult

  data class Failed(val exception: Exception) : RegistrationSendResult
}

/** Sends one built-in node registration. Never throws. */
fun interface RegistrationSender {
  fun send(definition: BuiltinNodeDefinition, workerId: String, registeredAt: Instant): RegistrationSendResult
}

/**
 * One send of one built-in node registration: a `FeatureRegistrationRequest` on the feature registration binding,
 * keyed by node id, exactly like a feature worker's registration.
 */
class BuiltinNodeRegistrationSender(
  private val streamBridge: StreamBridge
) : RegistrationSender {

  override fun send(definition: BuiltinNodeDefinition, workerId: String, registeredAt: Instant): RegistrationSendResult =
    try {
      val request = BuiltinNodeRegistrationRequests.build(
        definition = definition,
        workerId = workerId,
        registeredAt = registeredAt
      )
      val message = MessageBuilder
        .withPayload(request)
        .setHeader(KafkaHeaders.KEY, request.getNodeId())
        .build()
      val accepted = streamBridge.send(Channels.CONTINUUM_FEATURE_REGISTRATION_OUTPUT, message)
      if (accepted) RegistrationSendResult.Sent else RegistrationSendResult.NotAccepted
    } catch (exception: Exception) {
      RegistrationSendResult.Failed(exception = exception)
    }
}
