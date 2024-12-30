package com.continuum.core.commons.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class WorkflowSnapshot  @JsonCreator constructor(
    @JsonProperty("workflow_snapshot")
    val workflowSnapshot: ContinuumWorkflowModel? = null,
    @JsonProperty("nodeToInputsMap")
    val nodeToOutputsMap: Map<String, Map<String, PortData>>
)