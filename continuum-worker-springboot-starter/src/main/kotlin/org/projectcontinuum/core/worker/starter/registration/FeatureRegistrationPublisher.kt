package org.projectcontinuum.core.worker.starter.registration

import org.projectcontinuum.core.commons.event.Channels
import org.projectcontinuum.core.commons.node.ContinuumNodeModel
import org.projectcontinuum.core.protocol.event.FeatureRegistrationRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.context.event.EventListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Publishes feature registration requests to Kafka for every discovered [ContinuumNodeModel].
 *
 * At application startup (after all beans are initialized), this component iterates
 * through all registered node models and publishes a [FeatureRegistrationRequest]
 * for each one to the feature registration Kafka topic. This enables centralized
 * tracking of which nodes are available on which task queues.
 *
 * @property streamBridge Spring Cloud Stream bridge for publishing messages
 * @property nodeModels Provider for all registered [ContinuumNodeModel] beans
 * @property taskQueue The Temporal activity task queue this worker subscribes to
 */
@Component
class FeatureRegistrationPublisher(
  private val streamBridge: StreamBridge,
  private val nodeModels: ObjectProvider<ContinuumNodeModel>,
  @Value("\${continuum.core.worker.node-task-queue:ACTIVITY_TASK_QUEUE}")
  private val taskQueue: String
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(FeatureRegistrationPublisher::class.java)
    private val objectMapper = ObjectMapper()
  }

  @EventListener(ApplicationReadyEvent::class)
  fun publishFeatureRegistrations() {
    val workerId = "worker-${UUID.randomUUID()}"
    LOGGER.info("Publishing feature registrations for worker '$workerId' on task queue '$taskQueue'")

    nodeModels.forEach { nodeModel ->
      try {
        val request = buildRegistrationRequest(workerId, nodeModel)
        streamBridge.send(
          Channels.CONTINUUM_FEATURE_REGISTRATION_OUTPUT,
          MessageBuilder
            .withPayload(request)
            .setHeader(KafkaHeaders.KEY, request.getNodeId())
            .build()
        )
        LOGGER.info("Published feature registration for node: ${nodeModel.metadata.title} (${nodeModel.javaClass.name})")
      } catch (e: Exception) {
        LOGGER.error("Failed to publish feature registration for node: ${nodeModel.javaClass.name}", e)
      }
    }

    LOGGER.info("Feature registration complete. Published registrations for all discovered nodes.")
  }

  private fun buildRegistrationRequest(workerId: String, nodeModel: ContinuumNodeModel): FeatureRegistrationRequest {
    val nodeClassName = nodeModel.javaClass.name
    val featureId = deriveFeatureId(nodeModel)
    val nodeManifestJson = objectMapper.writeValueAsString(nodeModel.metadata)

    return FeatureRegistrationRequest.newBuilder()
      .setNodeId(nodeClassName)
      .setWorkerId(workerId)
      .setFeatureId(featureId)
      .setTaskQueue(taskQueue)
      .setNodeManifest(nodeManifestJson)
      .setDocumentationMarkdown(nodeModel.documentationMarkdown ?: "No documentation available")
      .setRegisteredAtTimestampUtc(Instant.now())
      .setExtensions(emptyMap())
      .build()
  }

  /**
   * Derives the feature ID from the node's package name by stripping the trailing ".node" segment.
   *
   * Examples:
   * - `org.projectcontinuum.feature.analytics.node.CreateTableNodeModel` → `org.projectcontinuum.feature.analytics`
   * - `org.projectcontinuum.feature.ai.node.UnslothTrainerNodeModel` → `org.projectcontinuum.feature.ai`
   * - `org.projectcontinuum.feature.template.node.ColumnJoinerNodeModel` → `org.projectcontinuum.feature.template`
   */
  private fun deriveFeatureId(nodeModel: ContinuumNodeModel): String {
    val packageName = nodeModel.javaClass.`package`.name
    return if (packageName.endsWith(".node")) {
      packageName.substringBeforeLast(".node")
    } else {
      packageName
    }
  }
}
