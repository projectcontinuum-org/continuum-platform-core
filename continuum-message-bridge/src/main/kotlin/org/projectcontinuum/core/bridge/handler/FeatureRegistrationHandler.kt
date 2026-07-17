package org.projectcontinuum.core.bridge.handler

import tools.jackson.databind.ObjectMapper
import org.projectcontinuum.core.bridge.service.NodeTreeSyncService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.protocol.event.FeatureRegistrationRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.function.Consumer

@Component
class FeatureRegistrationHandler(
  private val nodeTreeSyncService: NodeTreeSyncService,
  private val objectMapper: ObjectMapper
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
    val nodeManifest = request.nodeManifest.toString()
    val nodeData = objectMapper.readValue(nodeManifest, ContinuumWorkflowModel.NodeData::class.java)

    LOGGER.info("Received feature registration: node='${request.getNodeId()}', taskQueue='${request.getTaskQueue()}', worker='${request.getWorkerId()}'")

    nodeTreeSyncService.sync(
      nodeId = request.nodeId,
      name = nodeData.title,
      taskQueue = request.taskQueue,
      workerId = request.workerId,
      featureId = request.featureId,
      nodeManifest = nodeManifest,
      documentationMarkdown = request.documentationMarkdown.toString(),
      categories = request.categories.map { it.toString() },
      extensions = request.extensions.toString(),
      registeredAt = request.registeredAtTimestampUtc,
      lastSeenAt = now
    )

    LOGGER.info("Synced node tree entries for node: ${request.getNodeId()} on taskQueue '${request.getTaskQueue()}'")
  }
}
