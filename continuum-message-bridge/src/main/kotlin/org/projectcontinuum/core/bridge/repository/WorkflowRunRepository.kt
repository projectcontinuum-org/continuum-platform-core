package org.projectcontinuum.core.bridge.repository

import org.hibernate.validator.constraints.UUID
import org.projectcontinuum.core.bridge.entity.WorkflowRunEntity
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.Instant

interface WorkflowRunRepository: CrudRepository<WorkflowRunEntity, UUID> {
  @Modifying
  @Query("""
    UPDATE workflow_runs  SET
      progress_percentage = :progressPercentage ,
      status = :status,
      data = CAST(:data AS JSONB),
      updated_at = :updatedAt
    WHERE workflow_id = CAST(:workflowId AS UUID);
  """)
  fun upsert(
    workflowId: String,
    progressPercentage: Int,
    status: String,
    data: String,
    updatedAt: Instant
  ): Int
}