package org.projectcontinuum.core.api.server.entity.jpa

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import java.time.Instant
import java.util.*

@Entity
@Table(name = "workflow_schedules")
class WorkflowScheduleEntity(
  @Id
    @Column(name = "schedule_id")
    val scheduleId: UUID,

  @Column(name = "name", nullable = false)
    val name: String,

  @Column(name = "owned_by", nullable = false)
    val ownedBy: String,

  @Column(name = "cron_expression", nullable = false)
    val cronExpression: String,

  @Column(name = "time_zone")
    val timeZone: String? = null,

  @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow", columnDefinition = "jsonb", nullable = false)
    val workflow: ContinuumWorkflowModel,

  @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

  @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
