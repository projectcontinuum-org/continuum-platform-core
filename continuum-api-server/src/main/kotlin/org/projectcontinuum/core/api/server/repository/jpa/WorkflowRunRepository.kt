package org.projectcontinuum.core.api.server.repository.jpa

import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface WorkflowRunRepository : JpaRepository<WorkflowRunEntity, String>, JpaSpecificationExecutor<WorkflowRunEntity> {
    fun findByOwnedBy(ownedBy: String): List<WorkflowRunEntity>

    @Query(
        value = "SELECT DISTINCT CAST(w.workflow_uri AS VARCHAR) FROM workflow_runs w WHERE w.owned_by = :ownedBy ORDER BY CAST(w.workflow_uri AS VARCHAR)",
        countQuery = "SELECT COUNT(DISTINCT w.workflow_uri) FROM workflow_runs w WHERE w.owned_by = :ownedBy",
        nativeQuery = true
    )
    fun findDistinctWorkflowUris(@Param("ownedBy") ownedBy: String, pageable: Pageable): Page<String>

    fun existsByWorkflowIdAndOwnedBy(workflowId: String, ownedBy: String): Boolean

    /** True when [ownedBy] owns the run whose Temporal run id is [runId] (the id node outputs are stored under). */
    fun existsByRunIdAndOwnedBy(runId: String, ownedBy: String): Boolean

    /**
     * Sets only the run id, so it can never overwrite the status or outputs the message-bridge may already have
     * written for this row (a full `save()` would, since JPA merges every column).
     */
    @Modifying
    @Transactional
    @Query("UPDATE WorkflowRunEntity w SET w.runId = :runId WHERE w.workflowId = :workflowId")
    fun updateRunId(@Param("workflowId") workflowId: String, @Param("runId") runId: String): Int

    /**
     * Records that Temporal terminated the run: sets only `status` to `TERMINATED` and `updated_at` to [terminatedAt],
     * so the snapshot and outputs the message-bridge stored stay as they are. A run stuck retrying a workflow task never
     * runs its terminate signal handler, so nothing else would ever write its final status. A row that is already
     * `TERMINATED` (the run handled the signal and the bridge stored its event) is left untouched, so a healthy run
     * still ends exactly once. Returns the number of rows changed (0 or 1).
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE WorkflowRunEntity w SET w.status = 'TERMINATED', w.updatedAt = :terminatedAt " +
            "WHERE w.workflowId = :workflowId AND w.ownedBy = :ownedBy AND w.status <> 'TERMINATED'"
    )
    fun markTerminated(
        @Param("workflowId") workflowId: String,
        @Param("ownedBy") ownedBy: String,
        @Param("terminatedAt") terminatedAt: Instant
    ): Int
}
