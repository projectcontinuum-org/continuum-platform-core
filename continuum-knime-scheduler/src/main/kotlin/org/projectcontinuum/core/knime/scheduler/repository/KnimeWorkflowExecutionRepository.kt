package org.projectcontinuum.core.knime.scheduler.repository

import org.projectcontinuum.core.knime.scheduler.entity.KnimeWorkflowExecutionEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Every lookup is owner-scoped so an executionId belonging to another user is never
 * reachable through this repository.
 */
interface KnimeWorkflowExecutionRepository : JpaRepository<KnimeWorkflowExecutionEntity, UUID> {

  fun findByExecutionIdAndWorkflowIdAndOwnedBy(
    executionId: UUID,
    workflowId: UUID,
    ownedBy: String
  ): KnimeWorkflowExecutionEntity?

  fun findAllByWorkflowIdAndOwnedBy(workflowId: UUID, ownedBy: String, pageable: Pageable): Page<KnimeWorkflowExecutionEntity>
}
