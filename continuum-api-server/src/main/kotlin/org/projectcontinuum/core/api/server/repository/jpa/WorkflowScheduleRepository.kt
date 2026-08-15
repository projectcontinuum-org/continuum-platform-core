package org.projectcontinuum.core.api.server.repository.jpa

import org.projectcontinuum.core.api.server.entity.jpa.WorkflowScheduleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface WorkflowScheduleRepository :
  JpaRepository<WorkflowScheduleEntity, UUID>, JpaSpecificationExecutor<WorkflowScheduleEntity> {
  fun findByOwnedBy(ownedBy: String): List<WorkflowScheduleEntity>
  fun findByOwnedByAndName(ownedBy: String, name: String): List<WorkflowScheduleEntity>
}
