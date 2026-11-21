package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunSummaryEntity
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * Values of `workflow_runs.status`: `PENDING` from the API server, the rest from the orchestration service's run events.
 * The `allowableValues` of the `status` fields below repeat the list, because annotation arguments must be literals.
 */
object WorkflowRunStatusValues {
  const val DESCRIPTION =
    "Run status as last reported. `PENDING`: accepted, no event yet. `STARTED`: the orchestrator picked it up. " +
      "`RUNNING`: nodes are executing. `FINISHED`: every node is done (some may have failed or been skipped; see the " +
      "node statuses). `FAILED`: the run failed. `CANCELLED` / `TERMINATED`: stopped through the API."
}

@Schema(
  name = "WorkflowRunSummary",
  description = "One workflow run without its snapshot and outputs (the list view)."
)
data class WorkflowRunSummary(
  @field:Schema(description = "Workflow id returned by `POST /api/v1/workflow` (the Temporal workflow id).", example = "6b1f0e4e-8f4a-4d8c-9d61-3c2b7f1a9e10")
  val workflowId: String,
  @field:Schema(description = "Temporal workflow type. Always `IContinuumWorkflow` today.", example = "IContinuumWorkflow")
  val workflowType: String,
  @field:Schema(description = "Owner of the run (the `x-continuum-user-id` of the caller that started it).", example = "alice")
  val ownedBy: String,
  @field:Schema(description = "The workflow's `name`, normally the `.cwf` path in the workbench workspace.", type = "string", format = "uri-reference", example = "examples/text-cleanup.cwf")
  val workflowUri: URI,
  @field:Schema(description = "Schedule that started the run; null for runs started directly.", format = "uuid", nullable = true, example = "null")
  val scheduleId: UUID?,
  @field:Schema(description = "Share of nodes that are done, in percent (0-100).", minimum = "0", maximum = "100", example = "100")
  val progressPercentage: Int,
  @field:Schema(description = WorkflowRunStatusValues.DESCRIPTION, allowableValues = ["PENDING", "STARTED", "RUNNING", "FINISHED", "FAILED", "CANCELLED", "TERMINATED"], example = "FINISHED")
  val status: String,
  @field:Schema(description = "When the run was accepted (ISO-8601, UTC).", example = "2026-11-21T09:15:02.481Z")
  val createdAt: Instant,
  @field:Schema(description = "When the last run event was stored (ISO-8601, UTC).", example = "2026-11-21T09:15:09.112Z")
  val updatedAt: Instant
) {
  companion object {
    fun from(entity: WorkflowRunSummaryEntity) = WorkflowRunSummary(
      workflowId = entity.workflowId,
      workflowType = entity.workflowType,
      ownedBy = entity.ownedBy,
      workflowUri = entity.workflowUri,
      scheduleId = entity.scheduleId,
      progressPercentage = entity.progressPercentage,
      status = entity.status,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt
    )
  }
}

@Schema(
  name = "WorkflowRun",
  description = "One workflow run with the workflow as it ran (live node and edge statuses) and the outputs produced so far."
)
data class WorkflowRunResponse(
  @field:Schema(description = "Workflow id returned by `POST /api/v1/workflow` (the Temporal workflow id).", example = "6b1f0e4e-8f4a-4d8c-9d61-3c2b7f1a9e10")
  val workflowId: String,
  @field:Schema(description = "Temporal workflow type. Always `IContinuumWorkflow` today.", example = "IContinuumWorkflow")
  val workflowType: String,
  @field:Schema(description = "Owner of the run.", example = "alice")
  val ownedBy: String,
  @field:Schema(description = "The workflow's `name`, normally the `.cwf` path.", type = "string", format = "uri-reference", example = "examples/text-cleanup.cwf")
  val workflowUri: URI,
  @field:Schema(description = "Schedule that started the run; null for runs started directly.", format = "uuid", nullable = true, example = "null")
  val scheduleId: UUID?,
  @field:Schema(
    description = "Temporal run id. Node outputs are stored under it, so it is the `runId` of the node output endpoint. " +
      "Null until the run has started.",
    nullable = true,
    example = "0b7c6f0a-2a55-4f43-9a36-0c8f4d2b1e77"
  )
  val runId: String?,
  @field:Schema(description = "Share of nodes that are done, in percent (0-100).", minimum = "0", maximum = "100", example = "100")
  val progressPercentage: Int,
  @field:Schema(description = WorkflowRunStatusValues.DESCRIPTION, allowableValues = ["PENDING", "STARTED", "RUNNING", "FINISHED", "FAILED", "CANCELLED", "TERMINATED"], example = "FINISHED")
  val status: String,
  @field:Schema(description = "Snapshot of the workflow and its outputs as of the last run event.")
  val data: WorkflowRunDataResponse,
  @field:Schema(description = "When the run was accepted (ISO-8601, UTC).", example = "2026-11-21T09:15:02.481Z")
  val createdAt: Instant,
  @field:Schema(description = "When the last run event was stored (ISO-8601, UTC).", example = "2026-11-21T09:15:09.112Z")
  val updatedAt: Instant
) {
  companion object {
    fun from(entity: WorkflowRunEntity) = WorkflowRunResponse(
      workflowId = entity.workflowId,
      workflowType = entity.workflowType,
      ownedBy = entity.ownedBy,
      workflowUri = entity.workflowUri,
      scheduleId = entity.scheduleId,
      runId = entity.runId,
      progressPercentage = entity.progressPercentage,
      status = entity.status,
      data = WorkflowRunDataResponse(
        workflowSnapshot = entity.data.workflowSnapshot,
        nodeToOutputMap = entity.data.nodeToOutputMap
      ),
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt
    )
  }
}

@Schema(name = "WorkflowRunData", description = "The workflow as it ran and the outputs of its nodes.")
data class WorkflowRunDataResponse(
  @field:Schema(
    description = "The submitted workflow with the live `data.status` of every node and edge from the last run event."
  )
  val workflowSnapshot: ContinuumWorkflowModel,
  /**
   * Stored as JSONB and passed through unchanged, so rows written by any platform version stay readable. Every value
   * the platform writes is `node id -> port id -> PortData`; that is the documented shape.
   */
  @field:Schema(
    description = "Outputs per node: node id → port id → output. A port produced by the node points to its Parquet " +
      "file (`{remote}<runId>/<nodeId>/output.<port>.parquet`); a node that failed has the port `\$error` with the message.",
    implementation = NodeToOutputMapSchema::class,
    example = """{"normalize":{"data":{"status":"SUCCESS","contentType":"application/parquet","tableSpec":[{"name":"clean","contentType":"text/plain"}],"data":"{remote}0b7c6f0a-2a55-4f43-9a36-0c8f4d2b1e77/normalize/output.data.parquet"}}}"""
  )
  val nodeToOutputMap: Map<String, Any>
)

/** Documentation type only: node id → port id → [PortData]. Never instantiated. */
@Schema(name = "NodeToOutputMap", description = "Outputs per node: node id → port id → output.")
private abstract class NodeToOutputMapSchema : Map<String, Map<String, PortData>>

@Schema(name = "WorkflowRunCount", description = "Number of runs that match the filter.")
data class WorkflowRunCountResponse(
  @field:Schema(description = "Number of the caller's runs that match the filter.", example = "42")
  val count: Long
)
