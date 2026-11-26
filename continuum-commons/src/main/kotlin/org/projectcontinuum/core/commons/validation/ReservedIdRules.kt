package org.projectcontinuum.core.commons.validation

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

/**
 * Node keys and port ids must not start with the system prefix `$`: it is reserved for system keys such as `$error`
 * and `$variable` (see [NodeOutputSystemPort.isSystemPort]), which travel beside the data ports.
 */
internal object ReservedIdRules {

  const val SYSTEM_PREFIX = NodeOutputSystemPort.SYSTEM_PORT_PREFIX
  const val RESERVED_PREFIX_REASON =
    "which is reserved for system keys such as \$error and \$variable. Rename it so it does not start with '$SYSTEM_PREFIX'."

  /** The issue for a node key that uses the reserved prefix, or null. */
  fun nodeKeyIssue(key: String): WorkflowIssue? {
    if (!NodeOutputSystemPort.isSystemPort(key)) return null
    return WorkflowIssue(message = "Node key '$key' starts with '$SYSTEM_PREFIX', $RESERVED_PREFIX_REASON", node = key)
  }

  /** One issue per input, then per output port id of [definition] that uses the reserved prefix. */
  fun declaredPortIssues(key: String, definition: ContinuumWorkflowModel.NodeData, source: String): List<WorkflowIssue> {
    val reservedInputs = definition.inputs.orEmpty().keys.filter { portId -> NodeOutputSystemPort.isSystemPort(portId) }
    val reservedOutputs = definition.outputs.orEmpty().keys.filter { portId -> NodeOutputSystemPort.isSystemPort(portId) }
    val inputIssues = reservedInputs.map { portId ->
      declaredPortIssue(key = key, source = source, direction = "input", portId = portId)
    }
    val outputIssues = reservedOutputs.map { portId ->
      declaredPortIssue(key = key, source = source, direction = "output", portId = portId)
    }
    return inputIssues + outputIssues
  }

  /** The issue for an edge port ([side] `source` or `target`) that uses the reserved prefix. */
  fun edgePortIssue(label: String, side: String, portId: String?): WorkflowIssue =
    WorkflowIssue(
      message = "Edge $label: $side port '$portId' starts with '$SYSTEM_PREFIX', $RESERVED_PREFIX_REASON",
      edge = label
    )

  private fun declaredPortIssue(key: String, source: String, direction: String, portId: String): WorkflowIssue =
    WorkflowIssue(
      message = "Node '$key': the $source declares $direction port '$portId'. It starts with '$SYSTEM_PREFIX', $RESERVED_PREFIX_REASON",
      node = key
    )
}
