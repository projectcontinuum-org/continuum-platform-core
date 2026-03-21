package org.projectcontinuum.core.bridge.handler

import com.fasterxml.jackson.databind.ObjectMapper
import org.projectcontinuum.core.bridge.repository.WorkflowRunRepository
import org.projectcontinuum.core.commons.model.WorkflowUpdateEvent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.function.Consumer

/**
 * This handler listens to WorkflowUpdateEvent messages and syncs the workflow execution snapshot to the database.
 * It extracts the workflow ID from the Kafka message headers and updates the corresponding workflow run record in the database.
 * The workflow snapshot and node output map are stored as a JSON string in the data field of the workflow run record.
 */
@Component
class WorkflowExecutionSnapshotToDbSyncHandler(
  private val workflowRunRepository: WorkflowRunRepository
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(WorkflowExecutionSnapshotToDbSyncHandler::class.java)
    private val objectMapper = ObjectMapper()
  }

  @Bean("continuum-core-event-WorkflowExecutionSnapshotToDbSync-input")
  fun workflowExecutionSnapshotToDbSyncHandler(): Consumer<Message<WorkflowUpdateEvent>> = Consumer { message ->
    try {
      handle(message)
    } catch (e: Exception) {
      LOGGER.error("Error occurred in WorkflowExecutionSnapshot consumer.", e)
    }
  }

  fun handle(message: Message<WorkflowUpdateEvent>) {
    LOGGER.debug(
      "Syncing to DB: {}",
      message.payload
    )
    val workflowId = message.headers[KafkaHeaders.RECEIVED_KEY] as String
    workflowRunRepository.upsert(
      workflowId = workflowId,
      progressPercentage = message.payload.data.progressPercentage,
      status = message.payload.data.status,
      data = objectMapper.writeValueAsString(
        mapOf(
          "workflowSnapshot" to message.payload.data.workflow,
          "nodeToOutputMap" to message.payload.data.nodeToOutputsMap
        )
      ),
      updatedAt = Instant.now()
    )
  }
}