package org.projectcontinuum.core.bridge.repository

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals

/** Executes the repository's PostgreSQL upsert against a real server so its conflict guard is behavior-tested. */
@Testcontainers(disabledWithoutDocker = true)
class WorkflowRunRepositoryPostgresTest {

  private lateinit var jdbc: NamedParameterJdbcTemplate

  @BeforeEach
  fun setUp() {
    jdbc = NamedParameterJdbcTemplate(
      DriverManagerDataSource(
        postgreSql.jdbcUrl,
        postgreSql.username,
        postgreSql.password
      )
    )
    jdbc.jdbcTemplate.execute("DROP TABLE IF EXISTS workflow_runs")
    jdbc.jdbcTemplate.execute(
      """
        CREATE TABLE workflow_runs (
          workflow_id VARCHAR(255) PRIMARY KEY,
          workflow_type VARCHAR(255) NOT NULL,
          owned_by VARCHAR(255) NOT NULL,
          workflow_uri TEXT NOT NULL,
          schedule_id UUID,
          run_id VARCHAR(64),
          progress_percentage INTEGER NOT NULL,
          status VARCHAR(32) NOT NULL,
          data JSONB NOT NULL,
          created_at TIMESTAMPTZ NOT NULL,
          updated_at TIMESTAMPTZ NOT NULL
        )
      """.trimIndent()
    )
  }

  @Test
  fun `upsert updates active runs and retains an existing run id when an event omits it`() {
    val firstTime = Instant.parse("2026-01-01T00:00:00Z")
    val nextTime = firstTime.plusSeconds(1)

    assertEquals(
      expected = 1,
      actual = upsert(status = "RUNNING", runId = "run-1", progress = 10, data = "first", updatedAt = firstTime)
    )
    assertEquals(
      expected = 1,
      actual = upsert(status = "RUNNING", runId = null, progress = 20, data = "latest", updatedAt = nextTime)
    )

    val row = row()
    assertEquals(expected = "run-1", actual = row["run_id"])
    assertEquals(expected = 20, actual = row["progress_percentage"])
    assertEquals(expected = "RUNNING", actual = row["status"])
    assertEquals(expected = "latest", actual = row["snapshot"])
    assertUpdatedAt(expected = nextTime)
  }

  @Test
  fun `a late event for the terminated run cannot change its snapshot`() {
    val terminatedAt = Instant.parse("2026-01-01T00:00:02Z")
    upsert(status = "RUNNING", runId = "run-1", progress = 50, data = "before", updatedAt = terminatedAt.minusSeconds(1))
    upsert(status = "TERMINATED", runId = "run-1", progress = 50, data = "terminated", updatedAt = terminatedAt)

    val updateCount = upsert(
      status = "RUNNING",
      runId = "run-1",
      progress = 90,
      data = "late",
      updatedAt = terminatedAt.plusSeconds(1)
    )

    assertEquals(expected = 0, actual = updateCount)
    val row = row()
    assertEquals(expected = "TERMINATED", actual = row["status"])
    assertEquals(expected = 50, actual = row["progress_percentage"])
    assertEquals(expected = "terminated", actual = row["snapshot"])
    assertUpdatedAt(expected = terminatedAt)
  }

  @Test
  fun `a terminal event and a different run id can still update a terminated row`() {
    val terminatedAt = Instant.parse("2026-01-01T00:00:02Z")
    upsert(status = "TERMINATED", runId = "run-1", progress = 50, data = "first terminal", updatedAt = terminatedAt)

    assertEquals(
      expected = 1,
      actual = upsert(
        status = "TERMINATED",
        runId = "run-1",
        progress = 55,
        data = "final terminal",
        updatedAt = terminatedAt.plusSeconds(1)
      )
    )
    assertEquals(
      expected = 1,
      actual = upsert(
        status = "RUNNING",
        runId = "run-2",
        progress = 5,
        data = "new run",
        updatedAt = terminatedAt.plusSeconds(2)
      )
    )

    val row = row()
    assertEquals(expected = "run-2", actual = row["run_id"])
    assertEquals(expected = "RUNNING", actual = row["status"])
    assertEquals(expected = 5, actual = row["progress_percentage"])
    assertEquals(expected = "new run", actual = row["snapshot"])
  }

  private fun upsert(
    status: String,
    runId: String?,
    progress: Int,
    data: String,
    updatedAt: Instant
  ): Int = jdbc.update(
    upsertSql,
    mapOf(
      "workflowId" to "workflow-1",
      "workflowType" to "IContinuumWorkflow",
      "ownedBy" to "alice",
      "workflowUri" to "/workflow.cwf",
      "scheduleId" to null,
      "runId" to runId,
      "progressPercentage" to progress,
      "status" to status,
      "data" to """{"snapshot":"$data"}""",
      "updatedAt" to Timestamp.from(updatedAt)
    )
  )

  private fun row(): Map<String, Any?> = jdbc.queryForMap(
    """
      SELECT run_id, progress_percentage, status, data ->> 'snapshot' AS snapshot
      FROM workflow_runs
      WHERE workflow_id = :workflowId
    """.trimIndent(),
    mapOf("workflowId" to "workflow-1")
  )

  private fun assertUpdatedAt(expected: Instant) {
    val matches = jdbc.queryForObject(
      """
        SELECT updated_at = :expected
        FROM workflow_runs
        WHERE workflow_id = :workflowId
      """.trimIndent(),
      mapOf("expected" to Timestamp.from(expected), "workflowId" to "workflow-1"),
      Boolean::class.java
    )
    assertEquals(expected = true, actual = matches)
  }

  private val upsertSql: String = WorkflowRunRepository::class.java.getMethod(
    "upsert",
    String::class.java,
    String::class.java,
    String::class.java,
    String::class.java,
    String::class.java,
    String::class.java,
    Int::class.javaPrimitiveType,
    String::class.java,
    String::class.java,
    Instant::class.java
  ).getAnnotation(Query::class.java).value

  companion object {
    @Container
    @JvmField
    val postgreSql = PostgreSQLContainer<Nothing>("postgres:16-alpine")
  }
}
