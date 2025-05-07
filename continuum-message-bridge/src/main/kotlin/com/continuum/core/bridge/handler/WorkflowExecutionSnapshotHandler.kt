package com.continuum.core.bridge.handler

import com.continuum.core.commons.model.WorkflowUpdateEvent
import org.springframework.context.annotation.Bean
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.util.function.Consumer

@Component
class WorkflowExecutionSnapshotHandler {

    companion object {
        private val LOGGER = org.slf4j.LoggerFactory.getLogger(WorkflowExecutionSnapshotHandler::class.java)
    }

    @Bean("continuum-core-event-WorkflowExecutionSnapshot-input")
    fun executionSnapshotHandler(

    ): Consumer<Flux<Message<WorkflowUpdateEvent>>> = Consumer {
        it.map { message-> handle(message) }
            .onErrorContinue { t, u ->
                LOGGER.error("Error occurred in reactive consumer stream of manifestHandler.", t)
                LOGGER.error(t.printStackTrace().toString())
            }.subscribe()
    }

    fun handle(message: Message<WorkflowUpdateEvent>) {
        // Handle the message here
        // For example, you can log the message or process it further
        println("Received message: ${message.payload}")
    }

}