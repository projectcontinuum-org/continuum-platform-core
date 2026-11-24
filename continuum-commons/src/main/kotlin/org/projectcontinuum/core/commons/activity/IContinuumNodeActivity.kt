package org.projectcontinuum.core.commons.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import io.temporal.activity.ActivityInterface

@ActivityInterface
interface IContinuumNodeActivity {
  fun run(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>
  ): NodeActivityOutput

  /**
   * Reserved keys in a node's `inputs` / `outputs` maps that carry system data instead of a data port.
   *
   * - [ERROR]: present in `outputs` only when the node failed; `data` is the error message.
   * - [VARIABLE]: flow variables, a JSON object (`Map<String, Any>`) with content type `application/json`. In `outputs`
   *   it holds only the variables the node set itself; in `inputs` it holds the variables collected from all of the
   *   node's ancestors. Present only when the map is non-empty.
   *
   * Every ID starting with [SYSTEM_PORT_PREFIX] is reserved: no node ID or port ID may use it.
   */
  enum class NodeOutputSystemPort(
    val key: String
  ) {
    ERROR("\$error"),
    VARIABLE("\$variable");

    companion object {
      const val SYSTEM_PORT_PREFIX = "$"

      /** Whether [portId] is reserved for system keys (starts with [SYSTEM_PORT_PREFIX]). */
      fun isSystemPort(portId: String): Boolean = portId.startsWith(SYSTEM_PORT_PREFIX)
    }
  }

  data class NodeActivityOutput(
    val nodeId: String,
    val outputs: Map<String, PortData>
  )
}