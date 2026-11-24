package org.projectcontinuum.core.commons.utils

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus

/**
 * Reads and writes flow variables under the reserved [NodeOutputSystemPort.VARIABLE] key of a node's `inputs` or
 * `outputs` map. Flow variables are never null: a variable is either set to a non-null value or absent.
 */
object FlowVariables {

  /** Content type of the [NodeOutputSystemPort.VARIABLE] port data. */
  const val CONTENT_TYPE = "application/json"

  /**
   * Returns the flow variables stored under `"$variable"` in [ports], or an empty map when the key is absent or its
   * `data` is not a JSON object. Keys are converted to strings; entries with a null key or value are dropped.
   */
  fun extract(ports: Map<String, PortData>): Map<String, Any> {
    val rawVariables = ports[NodeOutputSystemPort.VARIABLE.key]?.data as? Map<*, *> ?: return emptyMap()
    val variables = LinkedHashMap<String, Any>()
    rawVariables.forEach { (name, value) ->
      if (name != null && value != null) {
        variables[name.toString()] = value
      }
    }
    return variables
  }

  /** Wraps [variables] as the port data stored under `"$variable"`. */
  fun toPortData(variables: Map<String, Any>): PortData = PortData(
    status = PortDataStatus.SUCCESS,
    contentType = CONTENT_TYPE,
    tableSpec = emptyList(),
    data = variables
  )

  /** Returns [ports] without any system key (`$error`, `$variable`, ...), leaving only data ports. */
  fun withoutSystemPorts(ports: Map<String, PortData>): Map<String, PortData> =
    ports.filterKeys { portId -> !NodeOutputSystemPort.isSystemPort(portId) }
}
