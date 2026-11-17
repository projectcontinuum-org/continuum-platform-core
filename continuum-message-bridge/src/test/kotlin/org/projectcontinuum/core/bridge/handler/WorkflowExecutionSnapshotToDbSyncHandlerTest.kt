package org.projectcontinuum.core.bridge.handler

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.bridge.repository.WorkflowRunRepository
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.WorkflowUpdate
import org.projectcontinuum.core.commons.model.WorkflowUpdateEvent
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.support.MessageBuilder
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowExecutionSnapshotToDbSyncHandlerTest {

  private lateinit var repository: WorkflowRunRepository
  private lateinit var handler: WorkflowExecutionSnapshotToDbSyncHandler

  @BeforeEach
  fun setUp() {
    repository = mock()
    whenever(
      repository.upsert(any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any(), any(), any())
    ).thenReturn(1)
    handler = WorkflowExecutionSnapshotToDbSyncHandler(repository, JsonMapper.builder().build())
  }

  private fun <T> anyOrNull(): T? = org.mockito.kotlin.anyOrNull()

  private fun update(runId: String?) = WorkflowUpdate(
    executionUUID = "wf-1",
    progressPercentage = 40,
    status = "RUNNING",
    nodeToOutputsMap = mapOf("2" to mapOf("data" to "{remote}run-1/2/output.data.parquet")),
    createdAtTimestampUtc = 1L,
    updatesAtTimestampUtc = 2L,
    workflow = ContinuumWorkflowModel(id = "id", name = "/w.cwf"),
    ownedBy = "alice",
    workflowType = "IContinuumWorkflow",
    workflowUri = "/w.cwf",
    scheduleId = null,
    runId = runId
  )

  private fun send(runId: String?) {
    val message = MessageBuilder
      .withPayload(WorkflowUpdateEvent(jobId = "wf-1", data = update(runId)))
      .setHeader(KafkaHeaders.RECEIVED_KEY, "wf-1")
      .build()
    handler.handle(message)
  }

  @Test
  fun `the run id from the event is written to the run row`() {
    send("run-1")

    val runId = argumentCaptor<String>()
    verify(repository).upsert(
      eq("wf-1"), eq("IContinuumWorkflow"), eq("alice"), eq("/w.cwf"), isNull(), runId.capture(),
      eq(40), eq("RUNNING"), any(), any<Instant>()
    )
    assertEquals("run-1", runId.firstValue)
  }

  @Test
  fun `an event from an older orchestration service without a run id still upserts`() {
    send(null)

    verify(repository).upsert(
      eq("wf-1"), any(), eq("alice"), any(), isNull(), isNull(), eq(40), any(), any(), any<Instant>()
    )
  }

  @Test
  fun `the upsert keeps an already recorded run id when a later event has none`() {
    val sql = WorkflowRunRepository::class.java.getMethod(
      "upsert", String::class.java, String::class.java, String::class.java, String::class.java, String::class.java,
      String::class.java, Int::class.javaPrimitiveType, String::class.java, String::class.java, Instant::class.java
    ).getAnnotation(org.springframework.data.jdbc.repository.query.Query::class.java).value

    assertTrue(sql.contains("run_id = COALESCE(EXCLUDED.run_id, workflow_runs.run_id)"), sql)
  }
}
