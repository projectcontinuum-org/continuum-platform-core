package com.continuum.core.worker.utils

import com.continuum.core.commons.event.Channels
import com.continuum.core.commons.model.WorkflowUpdateEvent
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

@Component
class StatusHelper(
    val streamBridge: StreamBridge
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(StatusHelper::class.java)
        private var streamBridge: StreamBridge? = null

        fun publishWorkflowSnapshot(
            workflowId: String,
            workflowSnapshot: WorkflowUpdateEvent
        ) {

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