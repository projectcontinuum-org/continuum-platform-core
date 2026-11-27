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
  val runId: String? = null,
  /** Events recorded in the run's Temporal history so far. Null on events published by an older orchestration service. */
  val historyEventCount: Long? = null,
  /** Temporal's hard cap on the events of one run's history ([HISTORY_EVENT_LIMIT]); the run fails beyond it. */
  val historyEventLimit: Long? = null
) {
  companion object {
    const val HISTORY_EVENT_LIMIT: Long = 51_200
  }
}