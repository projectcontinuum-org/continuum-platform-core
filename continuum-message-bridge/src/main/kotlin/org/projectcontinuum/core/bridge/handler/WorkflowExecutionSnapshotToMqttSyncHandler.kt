package org.projectcontinuum.core.bridge.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.projectcontinuum.core.commons.model.WorkflowUpdateEvent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.util.function.Consumer

/**
 * This handler listens to WorkflowUpdateEvent messages and forwards them to an MQTT topic.
 * The MQTT topic is structured as "continuum/workflow/execution/{workflowId}/update".
 * The message payload is serialized as JSON and sent with QoS level 1 and retained flag set to true.
 */
@Component
class WorkflowExecutionSnapshotToMqttSyncHandler(
  private val mqttClient: MqttClient
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(WorkflowExecutionSnapshotToMqttSyncHandler::class.java)
    private val MQTT_TOPIC_PREFIX = "continuum/workflow/execution"
    private val objectMapper = ObjectMapper()
  }

  @Bean("continuum-core-event-WorkflowExecutionSnapshot-input")
  fun workflowExecutionSnapshotToMqttSyncHandler(): Consumer<Message<WorkflowUpdateEvent>> = Consumer { message ->
    try {
      handle(message)
    } catch (e: Exception) {
      LOGGER.error("Error occurred in WorkflowExecutionSnapshot consumer.", e)
    }
  }

  fun handle(message: Message<WorkflowUpdateEvent>) {
    val messagePayloads = objectMapper
      .writeValueAsString(message.payload)
      .toByteArray(Charsets.UTF_8)
    LOGGER.debug("Forwarding to MQTT: ${String(messagePayloads)}")
    val workflowId = message.headers[KafkaHeaders.RECEIVED_KEY] as String
    val mqttMessage = MqttMessage().apply {
      payload = messagePayloads
      qos = 1
      isRetained = true
    }
    mqttClient.publish(
      "$MQTT_TOPIC_PREFIX/$workflowId/update",
      mqttMessage
    )
  }
}
