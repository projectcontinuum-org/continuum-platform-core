package org.projectcontinuum.core.knime.scheduler.repository

import org.projectcontinuum.core.knime.scheduler.entity.KnimeWorkflowEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Every lookup is owner-scoped so a workflowId belonging to another user is never
 * reachable through this repository.
 */
interface KnimeWorkflowRepository : JpaRepository<KnimeWorkflowEntity, UUID> {

  fun findByWorkflowIdAndOwnedBy(workflowId: UUID, ownedBy: String): KnimeWorkflowEntity?

  fun findAllByOwnedBy(ownedBy: String, pageable: Pageable): Page<KnimeWorkflowEntity>
}
