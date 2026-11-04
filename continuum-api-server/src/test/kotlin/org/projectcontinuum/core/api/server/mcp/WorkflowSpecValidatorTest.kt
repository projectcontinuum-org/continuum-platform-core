package org.projectcontinuum.core.api.server.mcp

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowSpecValidatorTest {

  private val table = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  private val reader = ContinuumWorkflowModel.NodeData(
    description = "Creates a table", title = "Create Table", nodeModel = "org.test.Reader",
    outputs = mapOf("data" to table),
    properties = mapOf("rows" to "[]"),
    propertiesSchema = mapOf(
      "type" to "object",
      "properties" to mapOf("rows" to mapOf("type" to "string")),
      "required" to listOf("rows")
    )
  )

  private val svg = ContinuumWorkflowModel.NodeData(
    description = "Renders molecules", title = "RDKit to SVG", nodeModel = "org.test.Svg",
    inputs = mapOf("input" to table),
    outputs = mapOf("output" to table),
    properties = mapOf("width" to 300, "format" to "svg"),
    propertiesSchema = mapOf(
      "type" to "object",
      "properties" to mapOf(
        "smilesColumn" to mapOf("type" to "string"),
        "width" to mapOf("type" to "integer", "minimum" to 50),
        "format" to mapOf("type" to "string", "enum" to listOf("svg", "png"))
      ),
      "required" to listOf("smilesColumn")
    )
  )

  private val service: NodeExplorerService = mock<NodeExplorerService>().also {
    whenever(it.getNodeMetadata(any())).thenReturn(null)
    whenever(it.getNodeMetadata("org.test.Reader")).thenReturn(reader)
    whenever(it.getNodeMetadata("org.test.Svg")).thenReturn(svg)
  }
  private val validator = WorkflowSpecValidator(service)

  private fun node(key: String?, nodeId: String?, properties: Map<String, Any?>? = null) = McpWorkflowSpecNode(key, nodeId, properties)
  private fun edge(from: String, fromPort: String, to: String, toPort: String) = McpWorkflowSpecEdge(from, fromPort, to, toPort)

  private val validNodes = listOf(node("r", "org.test.Reader"), node("s", "org.test.Svg", mapOf("smilesColumn" to "smiles")))
  private val validEdges = listOf(edge("r", "data", "s", "input"))

  private fun messages(result: McpWorkflowValidationResult) = result.errors.joinToString("\n") { it.message }

  @Test
  fun `a correct workflow is valid`() {
    val result = validator.validate("Render", validNodes, validEdges)

    assertTrue(result.valid, messages(result))
    assertEquals(2, result.nodeCount)
    assertEquals(1, result.edgeCount)
    assertEquals(emptyList(), result.warnings)
  }

  @Test
  fun `missing name and nodes are errors`() {
    val result = validator.validate(" ", null, null)

    assertFalse(result.valid)
    assertEquals(listOf("The workflow needs a non-empty name.", "The workflow needs at least one node."), result.errors.map { it.message })
  }

  @Test
  fun `unknown node id names the node key`() {
    val result = validator.validate("w", listOf(node("x", "RDKit2SVGNodeModel")), null)

    assertEquals(1, result.errors.size)
    assertEquals("x", result.errors.single().node)
    assertTrue(messages(result).contains("no registered node with id 'RDKit2SVGNodeModel'"))
  }

  @Test
  fun `blank and duplicate keys are errors`() {
    val result = validator.validate("w", listOf(node(null, "org.test.Reader"), node("r", "org.test.Reader"), node("r", "org.test.Reader")), null)

    assertTrue(messages(result).contains("Node #1 needs a non-empty key."))
    assertTrue(messages(result).contains("Node key 'r' is used more than once."))
  }

  @Test
  fun `unknown property lists the known ones`() {
    val nodes = listOf(node("r", "org.test.Reader"), node("s", "org.test.Svg", mapOf("smilesColumn" to "smiles", "height" to 10)))

    val error = validator.validate("w", nodes, validEdges).errors.single()

    assertEquals("height", error.property)
    assertEquals("Node 's': unknown property 'height'. Known properties: smilesColumn, width, format.", error.message)
  }

  @Test
  fun `missing required property is an error even though the schema default is unset`() {
    val nodes = listOf(node("r", "org.test.Reader"), node("s", "org.test.Svg"))

    val error = validator.validate("w", nodes, validEdges).errors.single()

    assertEquals("smilesColumn", error.property)
    assertTrue(error.message.contains("required property 'smilesColumn' has no value"))
  }

  @Test
  fun `wrong type, enum and minimum are reported from the JSON schema`() {
    val nodes = listOf(
      node("r", "org.test.Reader"),
      node("s", "org.test.Svg", mapOf("smilesColumn" to "smiles", "width" to "wide", "format" to "jpeg"))
    )

    val errors = validator.validate("w", nodes, validEdges).errors

    assertEquals(setOf("width", "format"), errors.map { it.property }.toSet(), errors.toString())
    assertTrue(errors.all { it.node == "s" && it.message.startsWith("Node 's' properties: /") }, errors.toString())

    val small = validator.validate("w", listOf(node("r", "org.test.Reader"), node("s", "org.test.Svg", mapOf("smilesColumn" to "s", "width" to 10))), validEdges)
    assertEquals("width", small.errors.single().property, small.errors.toString())
  }

  @Test
  fun `bad ports and unknown keys in edges list what exists`() {
    val edges = listOf(edge("r", "output", "s", "in"), edge("r", "data", "nope", "input"))

    val text = messages(validator.validate("w", validNodes, edges))

    assertTrue(text.contains("'org.test.Reader' has no output port 'output'. Output ports: data."), text)
    assertTrue(text.contains("'org.test.Svg' has no input port 'in'. Input ports: input."), text)
    assertTrue(text.contains("target node key 'nope' does not exist. Node keys: r, s."), text)
  }

  @Test
  fun `self loops, duplicates and doubly fed inputs are errors`() {
    val nodes = validNodes + node("r2", "org.test.Reader")
    val edges = listOf(
      edge("r", "data", "s", "input"),
      edge("r", "data", "s", "input"),
      edge("r2", "data", "s", "input"),
      edge("s", "output", "s", "input")
    )

    val text = messages(validator.validate("w", nodes, edges))

    assertTrue(text.contains("Edge r.data -> s.input is listed more than once."), text)
    assertTrue(text.contains("input port 'input' of node 's' is already fed by r.data"), text)
    assertTrue(text.contains("a node cannot connect to itself"), text)
  }

  @Test
  fun `a cycle is reported with the nodes in it`() {
    val nodes = listOf(node("a", "org.test.Svg", mapOf("smilesColumn" to "s")), node("b", "org.test.Svg", mapOf("smilesColumn" to "s")))
    val edges = listOf(edge("a", "output", "b", "input"), edge("b", "output", "a", "input"))

    val result = validator.validate("w", nodes, edges)

    assertEquals(
      listOf("The edges contain a cycle through nodes 'a', 'b'. A workflow must be a directed acyclic graph."),
      result.errors.map { it.message }
    )
  }

  @Test
  fun `unconnected nodes and inputs are warnings, not errors`() {
    val nodes = validNodes + node("extra", "org.test.Svg", mapOf("smilesColumn" to "s"))

    val result = validator.validate("w", nodes, validEdges)

    assertTrue(result.valid, messages(result))
    assertEquals(
      listOf(
        "Node 'extra' is not connected to any other node.",
        "Node 'extra': input port 'input' has no incoming edge. The node may fail if it needs this input."
      ),
      result.warnings.map { it.message }
    )
  }
}
