package org.projectcontinuum.core.commons.model

data class WorkflowUpdate(
  val executionUUID: String,
  val progressPercentage: Int,
  val status: String,
  val nodeToOutputsMap: Map<String, Any>,
  val createdAtTimestampUtc: Long,
  val updatesAtTimestampUtc: Long,
  val workflow: ContinuumWorkflowModel,
  val ownedBy: String,
  val workflowType: String,
  val workflowUri: String,
  val scheduleId: String? = null,
  /**
   * Temporal run id of this execution. Node outputs are stored under `<runId>/<nodeId>/...`, not under the
   * workflow id, so this is what ties a stored output back to its owning `workflow_runs` row. Null on events
   * published by an older orchestration service.
   */
  val runId: String? = null
)