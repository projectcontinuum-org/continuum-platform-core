package org.projectcontinuum.core.api.server.repository.jpa

import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunSummaryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface WorkflowRunSummaryRepository : JpaRepository<WorkflowRunSummaryEntity, String>, JpaSpecificationExecutor<WorkflowRunSummaryEntity>
