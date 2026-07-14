package org.projectcontinuum.core.bridge.entity

import org.projectcontinuum.core.bridge.model.WorkflowRunData
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

@Table("workflow_runs")
data class WorkflowRunEntity(
  @Id
  @Column("workflow_id")
  val workflowId: UUID,

  @Column("owned_by")
  val ownedBy: String,

  @Column("status")
  var status: String = "PENDING",

  @Column("data")
  var data: WorkflowRunData,

  @Column("created_at")
  val createdAt: Instant = Instant.now(),

  @Column("updated_at")
  var updatedAt: Instant = Instant.now()
)
