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
  fun `withoutSystemPorts keeps only data ports`() {
    val ports = mapOf(
      "out" to dataPort,
      NodeOutputSystemPort.VARIABLE.key to FlowVariables.toPortData(variables = mapOf("x" to 1)),
      NodeOutputSystemPort.ERROR.key to dataPort
    )

    assertEquals(setOf("out"), FlowVariables.withoutSystemPorts(ports = ports).keys)
  }
}
