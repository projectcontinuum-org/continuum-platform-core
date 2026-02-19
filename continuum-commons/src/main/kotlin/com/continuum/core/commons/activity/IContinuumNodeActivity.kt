package com.continuum.core.commons.activity

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import io.temporal.activity.ActivityInterface

@ActivityInterface
interface IContinuumNodeActivity {
  fun run(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>
  ): NodeActivityOutput

  enum class NodeOutputSystemPort(
    val key: String
  ) {
    ERROR("\$error")
  }

  data class NodeActivityOutput(
    val nodeId: String,
    val outputs: Map<String, PortData>
  )
}