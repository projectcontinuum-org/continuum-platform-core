package org.projectcontinuum.core.commons.utils

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import java.math.BigDecimal
import java.math.BigInteger

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

  /**
   * Whether two flow variable values are the same JSON value. Values reach the orchestrator after a JSON round trip,
   * so the same number can arrive as a different JVM type (`5` as `Int` or `Long`, `1.0` as `Double` next to `1`):
   * numbers are equal when their decimal values are equal. Maps are equal with the same keys and equal values (in any
   * key order), lists element by element in order. Everything else uses `==`, so `"5"` and `5` are different.
   */
  fun valuesEqual(first: Any?, second: Any?): Boolean = when {
    first is Number && second is Number -> numbersEqual(first = first, second = second)
    first is Map<*, *> && second is Map<*, *> ->
      first.size == second.size &&
        first.all { (key, value) -> second.containsKey(key) && valuesEqual(first = value, second = second[key]) }
    first is List<*> && second is List<*> ->
      first.size == second.size &&
        first.indices.all { index -> valuesEqual(first = first[index], second = second[index]) }
    else -> first == second
  }

  private fun numbersEqual(first: Number, second: Number): Boolean {
    val firstDecimal = toDecimalOrNull(first)
    val secondDecimal = toDecimalOrNull(second)
    if (firstDecimal == null || secondDecimal == null) return first == second
    return firstDecimal.compareTo(secondDecimal) == 0
  }

  /** The exact decimal value of [number], or null for NaN and infinities (which JSON cannot carry anyway). */
  private fun toDecimalOrNull(number: Number): BigDecimal? = when (number) {
    is BigDecimal -> number
    is BigInteger -> BigDecimal(number)
    is Double -> if (number.isFinite()) BigDecimal.valueOf(number) else null
    is Float -> if (number.isFinite()) BigDecimal(number.toString()) else null
    else -> number.toString().toBigDecimalOrNull()
  }
}
