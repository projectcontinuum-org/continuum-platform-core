package org.projectcontinuum.core.api.server.entity.jpa

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * Read-only projection of workflow_runs that excludes the heavy `data` JSONB column.
 * Used for list queries to avoid fetching large workflow snapshots from the database.
 */
@Entity
@Immutable
@Table(name = "workflow_runs")
class WorkflowRunSummaryEntity(
    @Id
    @Column(name = "workflow_id")
    val workflowId: UUID,

    @Column(name = "workflow_type", nullable = false)
    val workflowType: String,

    @Column(name = "owned_by", nullable = false)
    val ownedBy: String,

    @Column(name = "workflow_uri", nullable = false, columnDefinition = "VARCHAR(2048)")
    @Convert(converter = UriAttributeConverter::class)
    val workflowUri: URI,

    @Column(name = "progress_percentage", nullable = false)
    val progressPercentage: Int = 0,

    @Column(name = "status", nullable = false)
    val status: String = "PENDING",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
