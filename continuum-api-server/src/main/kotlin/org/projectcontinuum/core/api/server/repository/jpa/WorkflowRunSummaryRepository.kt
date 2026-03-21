package org.projectcontinuum.core.api.server.repository.jpa

import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunSummaryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface WorkflowRunSummaryRepository : JpaRepository<WorkflowRunSummaryEntity, UUID>, JpaSpecificationExecutor<WorkflowRunSummaryEntity>
