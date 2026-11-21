package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "WorkflowExecutionCount", description = "Number of the caller's Temporal executions that match the query.")
data class CountWorkflowResponse(
  @field:Schema(description = "Total number of matching executions.", example = "12")
  val count: Int,
  @field:Schema(description = "Counts per execution status; only filled when the query ends with `GROUP BY ExecutionStatus`.")
  val groups: List<Group>
) {
  @Schema(name = "WorkflowExecutionCountGroup", description = "Number of executions with one execution status.")
  data class Group(
    @field:Schema(
      description = "The group value as Temporal returns it: the JSON-encoded execution status (quotes included).",
      example = "\"Running\""
    )
    val name: String,
    @field:Schema(description = "Number of executions in this group.", example = "3")
    val count: Int
  )
}
