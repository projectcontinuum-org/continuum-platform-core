package org.projectcontinuum.core.commons.validation

import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class WorkflowValidatorTest {

  private val table = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  private val step = ContinuumWorkflowModel.NodeData(
    description = "", title = "Step", nodeModel = "org.test.Step",
    inputs = mapOf("in" to table), outputs = mapOf("out" to table),
    properties = mapOf("width" to 10),
    propertiesSchema = mapOf(
      "type" to "object",
      "properties" to mapOf("width" to mapOf("type" to "integer"), "name" to mapOf("type" to "string")),
      "required" to listOf("name")
    )
  )

  private val validator = WorkflowValidator { if (it == "org.test.Step") step else null }

  private fun node(key: String, properties: Map<String, Any?> = mapOf("name" to "n")) =
    GraphNode(key = key, nodeId = "org.test.Step", properties = properties)
  private fun edge(from: String, to: String) = GraphEdge(from = from, fromPort = "out", to = to, toPort = "in")

  private fun errors(nodes: List<GraphNode>, edges: List<GraphEdge>, mode: ValidationMode) =
    validator.validate(nodes = nodes, edges = edges, mode = mode).errors.joinToString("\n") { it.message }

  @Test
  fun `a chain is valid in both modes`() {
    for (mode in ValidationMode.values()) {
      val result = validator.validate(nodes = listOf(node("a"), node("b")), edges = listOf(edge("a", "b")), mode = mode)
      assertTrue(result.valid, result.errors.toString())
    }
  }

  @Test
  fun `a cycle names the nodes in it`() {
    val text = errors(
      nodes = listOf(node("a"), node("b")),
      edges = listOf(edge("a", "b"), edge("b", "a")),
      mode = ValidationMode.RUN
    )

    assertTrue(text.contains("cycle through nodes 'a', 'b'"), text)
  }

  @Test
  fun `self, duplicate and doubly fed edges are errors`() {
    val nodes = listOf(node("a"), node("b"), node("c"))
    val text = errors(
      nodes = nodes,
      edges = listOf(edge("a", "b"), edge("a", "b"), edge("c", "b"), edge("b", "b")),
      mode = ValidationMode.RUN
    )

    assertTrue(text.contains("listed more than once"), text)
    assertTrue(text.contains("already fed by a.out"), text)
    assertTrue(text.contains("cannot connect to itself"), text)
  }

  @Test
  fun `run mode checks the properties as sent, authoring merges defaults and rejects unknown keys`() {
    val extra = node("a", mapOf("name" to "n", "legacy" to true))

    assertTrue(validator.validate(nodes = listOf(extra), edges = emptyList(), mode = ValidationMode.RUN).valid)
    assertTrue(errors(nodes = listOf(extra), edges = emptyList(), mode = ValidationMode.AUTHORING).contains("unknown property 'legacy'"))

    val missing = node("a", emptyMap())
    assertTrue(errors(nodes = listOf(missing), edges = emptyList(), mode = ValidationMode.RUN).contains("name"))
  }

  @Test
  fun `a differing embedded definition is a warning and the registered one is validated`() {
    val embedded = step.copy(propertiesSchema = emptyMap(), inputs = emptyMap())

    val result = validator.validate(
      nodes = listOf(GraphNode(key = "a", nodeId = "org.test.Step", properties = mapOf("width" to "wide"), embedded = embedded)),
      edges = emptyList(),
      mode = ValidationMode.RUN
    )

    assertFalse(result.valid)
    assertTrue(result.errors.any { it.property == "width" }, result.errors.toString())
    assertTrue(result.warnings.any { it.message.contains("propertiesSchema and input ports differ") }, result.warnings.toString())
  }

  @Test
  fun `an unconnected input is only a warning`() {
    val result = validator.validate(nodes = listOf(node("a")), edges = emptyList(), mode = ValidationMode.RUN)

    assertTrue(result.valid)
    assertEquals(1, result.warnings.size)
    assertTrue(result.warnings.single().message.contains("skipped at run time"))
  }

  @Test
  fun `a node key starting with the reserved system prefix is an error in both modes`() {
    for (mode in ValidationMode.values()) {
      for (reservedKey in listOf("\$error", "\$variable", "\$foo")) {
        val result = validator.validate(
          nodes = listOf(node(key = reservedKey), node(key = "b")),
          edges = listOf(edge(from = reservedKey, to = "b")),
          mode = mode
        )

        val reservedKeyErrors = result.errors.filter { it.node == reservedKey && it.message.contains("reserved for system keys") }
        assertEquals(1, reservedKeyErrors.size, "mode $mode, key $reservedKey: ${result.errors}")
        assertFalse(
          result.errors.any { it.message.contains("does not exist") },
          "a reserved key must not also break its edges: ${result.errors}"
        )
      }
    }
  }

  @Test
  fun `edges using a reserved port id are errors without a duplicate missing-port error`() {
    val result = validator.validate(
      nodes = listOf(node(key = "a"), node(key = "b")),
      edges = listOf(
        GraphEdge(from = "a", fromPort = "\$variable", to = "b", toPort = "in"),
        GraphEdge(from = "a", fromPort = "out", to = "b", toPort = "\$error")
      ),
      mode = ValidationMode.RUN
    )
    val text = result.errors.joinToString("\n") { it.message }

    assertTrue(text.contains("source port '\$variable' starts with '\$'"), text)
    assertTrue(text.contains("target port '\$error' starts with '\$'"), text)
    assertFalse(text.contains("has no output port"), text)
    assertFalse(text.contains("has no input port"), text)
  }

  @Test
  fun `a submitted definition with reserved port ids is an error in run mode`() {
    val embedded = step.copy(
      inputs = mapOf("\$in" to table),
      outputs = mapOf("\$variable" to table)
    )

    val result = validator.validate(
      nodes = listOf(GraphNode(key = "a", nodeId = "org.test.Step", properties = mapOf("name" to "n"), embedded = embedded)),
      edges = emptyList(),
      mode = ValidationMode.RUN
    )
    val text = result.errors.joinToString("\n") { it.message }

    assertTrue(text.contains("declares input port '\$in'"), text)
    assertTrue(text.contains("declares output port '\$variable'"), text)
  }

  @Test
  fun `a registered definition with reserved port ids is an error`() {
    val legacyStep = step.copy(outputs = mapOf("\$legacy" to table))
    val legacyValidator = WorkflowValidator { if (it == "org.test.Step") legacyStep else null }

    val result = legacyValidator.validate(
      nodes = listOf(node(key = "a")),
      edges = emptyList(),
      mode = ValidationMode.AUTHORING
    )

    assertTrue(
      result.errors.any { it.message.contains("registered node 'org.test.Step' declares output port '\$legacy'") },
      result.errors.toString()
    )
  }
}
