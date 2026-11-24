package org.projectcontinuum.core.commons.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus

class FlowVariablesTest {

  private val dataPort = PortData(
    status = PortDataStatus.SUCCESS,
    contentType = "application/parquet",
    tableSpec = emptyList(),
    data = "{remote}run/node/output.out.parquet"
  )

  @Test
  fun `system keys are the reserved prefix`() {
    assertEquals("\$variable", NodeOutputSystemPort.VARIABLE.key)
    assertEquals("\$error", NodeOutputSystemPort.ERROR.key)
    assertTrue(NodeOutputSystemPort.isSystemPort("\$variable"))
    assertTrue(NodeOutputSystemPort.isSystemPort("\$anything"))
    assertFalse(NodeOutputSystemPort.isSystemPort("output-1"))
    assertFalse(NodeOutputSystemPort.isSystemPort("a\$b"))
  }

  @Test
  fun `extract returns an empty map when the key is absent or not an object`() {
    assertEquals(emptyMap<String, Any>(), FlowVariables.extract(ports = emptyMap()))
    assertEquals(emptyMap<String, Any>(), FlowVariables.extract(ports = mapOf("out" to dataPort)))

    val malformed = mapOf(NodeOutputSystemPort.VARIABLE.key to dataPort.copy(data = "not a map"))
    assertEquals(emptyMap<String, Any>(), FlowVariables.extract(ports = malformed))
  }

  @Test
  fun `extract reads the variables and drops null keys and values`() {
    val rawVariables = linkedMapOf<Any?, Any?>("x" to 1, "y" to null, null to "orphan", 7 to "seven")
    val ports = mapOf(
      "out" to dataPort,
      NodeOutputSystemPort.VARIABLE.key to dataPort.copy(data = rawVariables)
    )

    assertEquals(mapOf("x" to 1, "7" to "seven"), FlowVariables.extract(ports = ports))
  }

  @Test
  fun `toPortData wraps the variables as a JSON system port`() {
    val portData = FlowVariables.toPortData(variables = mapOf("x" to 1))

    assertEquals(PortDataStatus.SUCCESS, portData.status)
    assertEquals("application/json", portData.contentType)
    assertEquals(emptyList<Map<String, String>>(), portData.tableSpec)
    assertEquals(mapOf("x" to 1), portData.data)
  }

  @Test
  fun `valuesEqual treats the same JSON number as equal whatever its JVM type`() {
    assertTrue(FlowVariables.valuesEqual(first = 5, second = 5L))
    assertTrue(FlowVariables.valuesEqual(first = 1.0, second = 1))
    assertTrue(FlowVariables.valuesEqual(first = 2.50, second = java.math.BigDecimal("2.5")))
    assertTrue(FlowVariables.valuesEqual(first = java.math.BigInteger.TEN, second = 10.0f))
    assertTrue(FlowVariables.valuesEqual(first = 0.1, second = java.math.BigDecimal("0.1")))
    assertFalse(FlowVariables.valuesEqual(first = 5, second = 6L))
    assertFalse(FlowVariables.valuesEqual(first = 1.5, second = 1))
    assertFalse(FlowVariables.valuesEqual(first = Long.MAX_VALUE, second = Long.MAX_VALUE - 1))
  }

  @Test
  fun `valuesEqual compares maps and lists deeply and keeps types apart`() {
    val first = mapOf("limit" to 10, "columns" to listOf("a", 1), "nested" to mapOf("ratio" to 1.0))
    val sameInOtherForm = linkedMapOf("nested" to mapOf("ratio" to 1L), "columns" to listOf("a", 1.0), "limit" to 10L)

    assertTrue(FlowVariables.valuesEqual(first = first, second = sameInOtherForm), "key order and number types are ignored")
    assertFalse(FlowVariables.valuesEqual(first = listOf(1, 2), second = listOf(2, 1)), "list order matters")
    assertFalse(FlowVariables.valuesEqual(first = mapOf("a" to 1), second = mapOf("a" to 1, "b" to 2)))
    assertFalse(FlowVariables.valuesEqual(first = "5", second = 5), "a string is not a number")
    assertFalse(FlowVariables.valuesEqual(first = true, second = 1))
    assertTrue(FlowVariables.valuesEqual(first = "batch", second = "batch"))
  }

  @Test
  fun `withoutSystemPorts keeps only data ports`() {
    val ports = mapOf(
      "out" to dataPort,
      NodeOutputSystemPort.VARIABLE.key to FlowVariables.toPortData(variables = mapOf("x" to 1)),
      NodeOutputSystemPort.ERROR.key to dataPort
    )

    assertEquals(setOf("out"), FlowVariables.withoutSystemPorts(ports = ports).keys)
  }
}
