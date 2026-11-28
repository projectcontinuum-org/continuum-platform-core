package org.projectcontinuum.core.api.server.repository

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.api.server.openapi.FullContextWithoutExternalSystems
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals

/**
 * The query that records a terminated run, run against a real table (H2 in PostgreSQL mode). A run that is stuck
 * retrying a workflow task never publishes its final status, so after Temporal has terminated it the API server writes
 * `TERMINATED` itself. It must change only the status and the end time, only for the owner's row, and must not write a
 * row that already says `TERMINATED` again.
 */
@SpringBootTest(
  properties = [
    FullContextWithoutExternalSystems.MAIN_APPLICATION_YML,
    "spring.datasource.url=jdbc:h2:mem:run-termination;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    FullContextWithoutExternalSystems.H2_DRIVER,
    FullContextWithoutExternalSystems.H2_USER,
    FullContextWithoutExternalSystems.H2_PASSWORD,
    FullContextWithoutExternalSystems.H2_DIALECT,
    FullContextWithoutExternalSystems.NO_SCHEMA_INIT,
    FullContextWithoutExternalSystems.NO_MINIO_BUCKET_INIT,
    FullContextWithoutExternalSystems.TEMPORAL_TARGET
  ]
)
class WorkflowRunTerminationRecordTest : FullContextWithoutExternalSystems() {

  @Autowired
  private lateinit var workflowRunRepository: WorkflowRunRepository

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  private val lastEventAt: Instant = Instant.parse("2026-11-26T06:41:05Z")
  private val snapshot = """{"workflowSnapshot":{"id":"wf","name":"loops.cwf"},"nodeToOutputMap":{"L1":{}}}"""

  @BeforeEach
  fun createTable() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS workflow_runs")
    jdbcTemplate.execute(
      """
      CREATE TABLE workflow_runs (
          workflow_id         VARCHAR(255) PRIMARY KEY,
          workflow_type       VARCHAR(255) NOT NULL,
          owned_by            VARCHAR(255) NOT NULL,
          workflow_uri        VARCHAR(2048) NOT NULL,
          schedule_id         UUID,
          run_id              VARCHAR(64),
          progress_percentage INT          NOT NULL DEFAULT 0,
          status              VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
          data                VARCHAR(4000) NOT NULL DEFAULT '{}',
          created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
          updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
      )
      """.trimIndent()
    )
  }

  private fun insertRun(workflowId: String, ownedBy: String, status: String) {
    jdbcTemplate.update(
      "INSERT INTO workflow_runs (workflow_id, workflow_type, owned_by, workflow_uri, run_id, progress_percentage, " +
        "status, data, created_at, updated_at) VALUES (?, 'IContinuumWorkflow', ?, 'loops.cwf', 'run-id-1', 83, ?, ?, ?, ?)",
      workflowId, ownedBy, status, snapshot, Timestamp.from(lastEventAt), Timestamp.from(lastEventAt)
    )
  }

  private fun column(workflowId: String, name: String): Any? =
    jdbcTemplate.queryForMap("SELECT * FROM workflow_runs WHERE workflow_id = ?", workflowId)[name]

  /** Rows whose `updated_at` is still the time of the run's last event. */
  private fun rowsStillAtLastEvent(): Long =
    jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workflow_runs WHERE updated_at = ?", Long::class.java, Timestamp.from(lastEventAt))!!

  @Test
  fun `a running record becomes terminated with the end time, and its snapshot and progress are kept`() {
    insertRun(workflowId = "stuck", ownedBy = "alice", status = "RUNNING")
    val terminatedAt = Instant.parse("2026-11-26T06:42:59.123Z")

    val changedRows = workflowRunRepository.markTerminated(workflowId = "stuck", ownedBy = "alice", terminatedAt = terminatedAt)

    assertEquals(1, changedRows)
    assertEquals("TERMINATED", column(workflowId = "stuck", name = "STATUS"))
    assertEquals(0L, rowsStillAtLastEvent(), "the end time is written")
    assertEquals(snapshot, column(workflowId = "stuck", name = "DATA"))
    assertEquals(83, column(workflowId = "stuck", name = "PROGRESS_PERCENTAGE"))
    assertEquals("run-id-1", column(workflowId = "stuck", name = "RUN_ID"))
  }

  @Test
  fun `a record the run already ended as terminated is not written again`() {
    insertRun(workflowId = "healthy", ownedBy = "alice", status = "TERMINATED")

    val changedRows = workflowRunRepository.markTerminated(workflowId = "healthy", ownedBy = "alice", terminatedAt = Instant.now())

    assertEquals(0, changedRows)
    assertEquals("TERMINATED", column(workflowId = "healthy", name = "STATUS"))
    assertEquals(1L, rowsStillAtLastEvent(), "the end time the run itself reported stays")
  }

  @Test
  fun `another owner's record with the same workflow id is never changed`() {
    insertRun(workflowId = "stuck", ownedBy = "alice", status = "RUNNING")

    val changedRows = workflowRunRepository.markTerminated(workflowId = "stuck", ownedBy = "mallory", terminatedAt = Instant.now())

    assertEquals(0, changedRows)
    assertEquals("RUNNING", column(workflowId = "stuck", name = "STATUS"))
    assertEquals(1L, rowsStillAtLastEvent())
  }
}
