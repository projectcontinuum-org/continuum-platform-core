package org.projectcontinuum.core.commons.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class WorkflowSnapshot @JsonCreator constructor(
  @param:JsonProperty("workflow_snapshot")
  val workflowSnapshot: ContinuumWorkflowModel? = null,
  @param:JsonProperty("nodeToInputsMap")
  val nodeToOutputsMap: Map<String, Map<String, PortData>>
)