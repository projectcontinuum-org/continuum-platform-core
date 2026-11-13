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

interface WorkflowRunRepository : JpaRepository<WorkflowRunEntity, String>, JpaSpecificationExecutor<WorkflowRunEntity> {
    fun findByOwnedBy(ownedBy: String): List<WorkflowRunEntity>

    @Query(
        value = "SELECT DISTINCT CAST(w.workflow_uri AS VARCHAR) FROM workflow_runs w WHERE w.owned_by = :ownedBy ORDER BY CAST(w.workflow_uri AS VARCHAR)",
        countQuery = "SELECT COUNT(DISTINCT w.workflow_uri) FROM workflow_runs w WHERE w.owned_by = :ownedBy",
        nativeQuery = true
    )
    fun findDistinctWorkflowUris(@Param("ownedBy") ownedBy: String, pageable: Pageable): Page<String>

    @Query(
        value = "SELECT w.workflow_id FROM workflow_runs w WHERE w.owned_by = :ownedBy",
        nativeQuery = true
    )
    fun findWorkflowIdsByOwnedBy(@Param("ownedBy") ownedBy: String): List<String>

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
}
