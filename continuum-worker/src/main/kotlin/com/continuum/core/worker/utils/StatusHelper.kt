package com.continuum.core.worker.utils

import com.continuum.core.commons.event.Channels
import com.continuum.core.commons.model.WorkflowUpdateEvent
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.context.annotation.Profile
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

@Profile("kafka_event")
@Component
class StatusHelper(
    val streamBridge: StreamBridge
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(StatusHelper::class.java)
//        private val objectMapper = ObjectMapper()
//        private val MQTT_TOPIC_PREFIX = "continuum/workflow/execution"
//        private val MQTT_CLIENT_ID = System.getenv().getOrDefault("MQTT_CLIENT_ID", UUID.randomUUID().toString())
//        private val options = MqttConnectOptions().apply {
//            isAutomaticReconnect = true
//            isCleanSession = true
//            connectionTimeout = 10
//
//        }
//        private val mqttClient = MqttClient("tcp://localhost:31883", MQTT_CLIENT_ID).also {
//            it.connect(options)
//        }
        private var streamBridge: StreamBridge? = null

        fun publishWorkflowSnapshot(
            workflowId: String,
            workflowSnapshot: WorkflowUpdateEvent
        ) {
//            while(!mqttClient.isConnected) {
//                LOGGER.warn("MQTT client is not connected. Waiting for updates to continue...")
//                Thread.sleep(1000)
//            }
//            LOGGER.info("Publishing workflow snapshot for workflowId: $workflowId ...")
//            mqttClient.publish(
//                "$MQTT_TOPIC_PREFIX/$workflowId/update",
//                MqttMessage().apply {
//                    payload = objectMapper.writeValueAsString(workflowSnapshot).toByteArray(Charsets.UTF_8)
//                    qos = 1
//                    isRetained = true
//                }
//            )

            if(streamBridge != null) {
                try {
                    streamBridge!!.send(
                        Channels.CONTINUUM_WORKFLOW_STATE_CHANGE_EVENT,
                        MessageBuilder
                            .withPayload(workflowSnapshot)
                            .setHeader(KafkaHeaders.KEY, workflowId)
                            .setHeader("content-type", "application/json")
                            .build()
                    )
                } catch (ex: RuntimeException) {
                    LOGGER.error("Unable to send status", ex)
                }
            } else {
                LOGGER.warn("StreamBridge is null. Cannot send message to Kafka.")
            }
        }
    }

    @PostConstruct
    fun postConstruct() {
        StatusHelper.streamBridge = this.streamBridge
    }
}