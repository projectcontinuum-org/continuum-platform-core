package org.projectcontinuum.core.commons.activity

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
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