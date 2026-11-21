package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema
import io.temporal.api.enums.v1.WorkflowExecutionStatus

@Schema(name = "WorkflowExecutionStatus", description = "A workflow execution as Temporal sees it.")
data class WorkflowStatus(
  @field:Schema(description = "Workflow id.", example = "6b1f0e4e-8f4a-4d8c-9d61-3c2b7f1a9e10")
  val workflowId: String,
  @field:Schema(description = "Temporal workflow type.", example = "IContinuumWorkflow")
  val type: String,
  @field:Schema(
    description = "Temporal execution status.",
    type = "string",
    allowableValues = [
      "WORKFLOW_EXECUTION_STATUS_UNSPECIFIED", "WORKFLOW_EXECUTION_STATUS_RUNNING", "WORKFLOW_EXECUTION_STATUS_COMPLETED",
      "WORKFLOW_EXECUTION_STATUS_FAILED", "WORKFLOW_EXECUTION_STATUS_CANCELED", "WORKFLOW_EXECUTION_STATUS_TERMINATED",
      "WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW", "WORKFLOW_EXECUTION_STATUS_TIMED_OUT"
    ],
    example = "WORKFLOW_EXECUTION_STATUS_COMPLETED"
  )
  val temporalStatus: WorkflowExecutionStatus,
  @field:Schema(
    description = "Temporal search attributes of the execution (except `BuildIds`): name → raw JSON-encoded value. " +
      "`Continuum:WorkflowFileName` is the workflow name, `Continuum:ExecutionStatus` the numeric execution status.",
    example = "{\"Continuum:WorkflowFileName\": \"\\\"examples/text-cleanup.cwf\\\"\", \"Continuum:ExecutionStatus\": \"2\"}"
  )
  val metadata: Map<String, String>
)
