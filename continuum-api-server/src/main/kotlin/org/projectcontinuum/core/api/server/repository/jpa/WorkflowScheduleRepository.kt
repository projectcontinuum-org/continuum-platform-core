package org.projectcontinuum.core.api.server.repository.jpa

import org.projectcontinuum.core.api.server.entity.jpa.WorkflowScheduleEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowScheduleRepository : JpaRepository<WorkflowScheduleEntity, UUID> {
  fun findByOwnedBy(ownedBy: String): List<WorkflowScheduleEntity>
  fun findByOwnedByAndName(ownedBy: String, name: String): List<WorkflowScheduleEntity>
}
