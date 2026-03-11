package org.projectcontinuum.core.bridge.handler

import org.projectcontinuum.core.bridge.repository.RegisteredNodeRepository
import org.projectcontinuum.core.protocol.event.FeatureRegistrationRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.function.Consumer

@Component
class FeatureRegistrationHandler(
  private val registeredNodeRepository: RegisteredNodeRepository
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(FeatureRegistrationHandler::class.java)
  }

  @Bean("continuum-core-event-FeatureRegistration-input")
  fun featureRegistrationHandler(): Consumer<Message<FeatureRegistrationRequest>> = Consumer { message ->
    try {
      handle(message)
    } catch (e: Exception) {
      LOGGER.error("Error processing feature registration message", e)
    }
  }

  private fun handle(message: Message<FeatureRegistrationRequest>) {
    val request = message.payload
    val now = Instant.now()

    LOGGER.info("Received feature registration: node='${request.getNodeId()}', taskQueue='${request.getTaskQueue()}', worker='${request.getWorkerId()}'")

    registeredNodeRepository.upsert(
      nodeId = request.getNodeId(),
      taskQueue = request.getTaskQueue(),
      workerId = request.getWorkerId(),
      featureId = request.getFeatureId(),
      nodeManifest = request.getNodeManifest().toString(),
      documentationMarkdown = request.getDocumentationMarkdown().toString(),
      extensions = request.getExtensions().toString(),
      registeredAt = request.getRegisteredAtTimestampUtc(),
      lastSeenAt = now
    )

    LOGGER.info("Upserted registered node: ${request.getNodeId()} on taskQueue '${request.getTaskQueue()}'")
  }
}
