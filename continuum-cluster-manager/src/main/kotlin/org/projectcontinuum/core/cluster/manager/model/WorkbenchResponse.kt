package org.projectcontinuum.core.cluster.manager.model

import java.time.Instant
import java.util.UUID

data class WorkbenchResponse(
  val instanceId: UUID,
  val instanceName: String,
  val namespace: String,
  val userId: String,
  val status: String,
  val image: String,
  val resources: ResourceSpec,
  val serviceEndpoint: String?,
  val createdAt: Instant,
  val updatedAt: Instant
)
