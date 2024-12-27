package com.continuum.core.worker.utils

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.WorkflowSnapshot
import com.continuum.core.worker.workflow.ContinuumWorkflow
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.slf4j.LoggerFactory
import java.util.UUID

class MqttHelper {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MqttHelper::class.java)
        private val objectMapper = ObjectMapper()
        private val MQTT_TOPIC_PREFIX = "continuum/workflow/execution"
        private val MQTT_CLIENT_ID = System.getenv().getOrDefault("MQTT_CLIENT_ID", UUID.randomUUID().toString())
        private val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10

        }
        private val mqttClient = MqttClient("tcp://localhost:31883", MQTT_CLIENT_ID).also {
            it.connect(options)
        }

        fun publishWorkflowSnapshot(
            workflowId: String,
            workflowSnapshot: WorkflowUpdateEvent
        ) {
            if(!mqttClient.isConnected) {
                LOGGER.error("MQTT client is not connected")
                return
            }
            LOGGER.info("Publishing workflow snapshot for workflowId: $workflowId ...")
            mqttClient.publish(
                "$MQTT_TOPIC_PREFIX/$workflowId/update",
                MqttMessage().apply {
                    payload = objectMapper.writeValueAsString(workflowSnapshot).toByteArray(Charsets.UTF_8)
                    qos = 1
                    isRetained = true
                }
            )
        }
    }

    data class WorkflowUpdateEvent(
        val jobId: String,
        val data: WorkflowUpdate
    )

    data class WorkflowUpdate(
        val executionUUID: String,
        val progressPercentage: Int,
        val status: String,
        val nodeToOutputsMap: Map<String, Any>,
        val createdAtTimestampUtc: Long,
        val updatesAtTimestampUtc: Long,
        val workflow: ContinuumWorkflowModel
    )
}