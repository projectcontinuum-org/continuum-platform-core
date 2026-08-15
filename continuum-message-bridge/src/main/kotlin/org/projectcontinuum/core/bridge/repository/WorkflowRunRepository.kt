package org.projectcontinuum.core.bridge.repository

import org.projectcontinuum.core.bridge.entity.WorkflowRunEntity
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface WorkflowRunRepository: CrudRepository<WorkflowRunEntity, String> {
  @Modifying
  @Query("""
    INSERT INTO workflow_runs (workflow_id, workflow_type, owned_by, workflow_uri, schedule_id, progress_percentage, status, data, created_at, updated_at)
    VALUES (:workflowId, :workflowType, :ownedBy, :workflowUri, CAST(:scheduleId AS UUID), :progressPercentage, :status, CAST(:data AS JSONB), :updatedAt, :updatedAt)
    ON CONFLICT (workflow_id) DO UPDATE SET
      progress_percentage = EXCLUDED.progress_percentage,
      status = EXCLUDED.status,
      data = EXCLUDED.data,
      updated_at = EXCLUDED.updated_at;
  """)
  fun upsert(
    workflowId: String,
    workflowType: String,
    ownedBy: String,
    workflowUri: String,
    scheduleId: String?,
    progressPercentage: Int,
    status: String,
    data: String,
    updatedAt: Instant
  ): Int
}
