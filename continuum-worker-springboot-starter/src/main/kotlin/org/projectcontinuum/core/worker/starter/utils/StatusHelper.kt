package org.projectcontinuum.core.worker.starter.utils

import org.projectcontinuum.core.commons.event.Channels
import org.projectcontinuum.core.commons.model.WorkflowUpdateEvent
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/**
 * Utility component for publishing workflow status updates to Kafka.
 *
 * This class provides a bridge between Temporal workflows and the Kafka messaging
 * system, enabling real-time status updates to be consumed by the UI and other
 * services. It uses Spring Cloud Stream's [StreamBridge] for message publishing.
 *
 * ## Design Pattern
 * This class uses a static companion object pattern to allow access from within
 * Temporal workflow code, which cannot use Spring dependency injection directly.
 * The instance's [StreamBridge] is copied to the companion object during
 * [PostConstruct] initialization.
 *
 * ## Usage
 * From within a Temporal workflow:
 * ```kotlin
 * StatusHelper.publishWorkflowSnapshot(workflowId, updateEvent)
 * ```
 *
 * ## Message Format
 * Messages are published with:
 * - Kafka key: workflow ID (for partitioning)
 * - Content-Type: application/json
 * - Channel: [Channels.CONTINUUM_WORKFLOW_STATE_CHANGE_EVENT]
 *
 * @property streamBridge Spring Cloud Stream bridge for publishing messages
 * @author Continuum Team
 * @since 1.0.0
 * @see WorkflowUpdateEvent
 * @see Channels
 */
@Component
class StatusHelper(
  val streamBridge: StreamBridge
) {
  companion object {
    /** Logger instance for this class */
    private val LOGGER = LoggerFactory.getLogger(StatusHelper::class.java)

    /** Static reference to StreamBridge for use from Temporal workflows */
    private var streamBridge: StreamBridge? = null

    /**
     * Publishes a workflow snapshot to Kafka.
     *
     * This method sends a [WorkflowUpdateEvent] to the workflow state change event
     * channel, allowing subscribers (like the UI) to receive real-time updates
     * about workflow execution progress.
     *
     * **Thread Safety**: This method is thread-safe and can be called from multiple
     * concurrent workflow executions.
     *
     * @param workflowId The unique identifier of the workflow (used as Kafka message key)
     * @param workflowSnapshot The workflow update event containing current state
     */
    fun publishWorkflowSnapshot(
      workflowId: String,
      workflowSnapshot: WorkflowUpdateEvent
    ) {

      if (streamBridge != null) {
        try {
          // Send message to Kafka with workflow ID as the partition key
          streamBridge!!.send(
            Channels.CONTINUUM_WORKFLOW_STATE_CHANGE_EVENT,
            MessageBuilder
              .withPayload(workflowSnapshot)
              .setHeader(KafkaHeaders.KEY, workflowId)
              .setHeader("content-type", "application/json")
              .build()
          )
        } catch (ex: RuntimeException) {
          // Log error but don't fail the workflow - status updates are best-effort
          LOGGER.error("Unable to send status", ex)
        }
      } else {
        // StreamBridge not yet initialized - this can happen during startup
        LOGGER.warn("StreamBridge is null. Cannot send message to Kafka.")
      }
    }
  }

  /**
   * Initializes the static StreamBridge reference after bean construction.
   *
   * This method copies the instance's [StreamBridge] to the companion object,
   * enabling static access from Temporal workflow code.
   */
  @PostConstruct
  fun postConstruct() {
    StatusHelper.streamBridge = this.streamBridge
  }
}