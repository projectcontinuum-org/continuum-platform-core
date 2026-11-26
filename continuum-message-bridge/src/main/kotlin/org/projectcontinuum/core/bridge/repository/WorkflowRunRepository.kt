package org.projectcontinuum.core.bridge.repository

import org.projectcontinuum.core.bridge.entity.WorkflowRunEntity
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface WorkflowRunRepository: CrudRepository<WorkflowRunEntity, String> {
  /**
   * Inserts or updates the run row from one run event; `run_id` is kept when a later event has none.
   *
   * A row that says `TERMINATED` ignores a later non-`TERMINATED` event of the same run. The api-server writes
   * `TERMINATED` itself once Temporal has terminated a run that could not report it (a run stuck retrying a workflow
   * task), and an event that run published before the terminate may reach the bridge only afterwards; it must not turn
   * the record back to `RUNNING`. The run's own `TERMINATED` event is still stored, and an event of another run under
   * the same workflow id (a run id that differs from the stored one) is applied as before.
   */
  @Modifying
  @Query("""
    INSERT INTO workflow_runs (workflow_id, workflow_type, owned_by, workflow_uri, schedule_id, run_id, progress_percentage, status, data, created_at, updated_at)
    VALUES (:workflowId, :workflowType, :ownedBy, :workflowUri, CAST(:scheduleId AS UUID), :runId, :progressPercentage, :status, CAST(:data AS JSONB), :updatedAt, :updatedAt)
    ON CONFLICT (workflow_id) DO UPDATE SET
      run_id = COALESCE(EXCLUDED.run_id, workflow_runs.run_id),
      progress_percentage = EXCLUDED.progress_percentage,
      status = EXCLUDED.status,
      data = EXCLUDED.data,
      updated_at = EXCLUDED.updated_at
    WHERE workflow_runs.status <> 'TERMINATED'
      OR EXCLUDED.status = 'TERMINATED'
      OR EXCLUDED.run_id <> workflow_runs.run_id;
  """)
  fun upsert(
    workflowId: String,
    workflowType: String,
    ownedBy: String,
    workflowUri: String,
    scheduleId: String?,
    runId: String?,
    progressPercentage: Int,
    status: String,
    data: String,
    updatedAt: Instant
  ): Int
}
