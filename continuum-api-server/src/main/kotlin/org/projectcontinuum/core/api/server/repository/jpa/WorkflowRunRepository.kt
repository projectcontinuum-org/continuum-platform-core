package org.projectcontinuum.core.api.server.repository.jpa

import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WorkflowRunRepository : JpaRepository<WorkflowRunEntity, UUID>, JpaSpecificationExecutor<WorkflowRunEntity> {
    fun findByOwnedBy(ownedBy: String): List<WorkflowRunEntity>

    @Query(
        value = "SELECT DISTINCT CAST(w.workflow_uri AS VARCHAR) FROM workflow_runs w WHERE w.owned_by = :ownedBy ORDER BY CAST(w.workflow_uri AS VARCHAR)",
        countQuery = "SELECT COUNT(DISTINCT w.workflow_uri) FROM workflow_runs w WHERE w.owned_by = :ownedBy",
        nativeQuery = true
    )
    fun findDistinctWorkflowUris(@Param("ownedBy") ownedBy: String, pageable: Pageable): Page<String>
}
