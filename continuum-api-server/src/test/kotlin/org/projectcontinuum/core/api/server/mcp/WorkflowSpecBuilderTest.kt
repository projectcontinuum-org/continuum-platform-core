package org.projectcontinuum.core.api.server.mcp

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowSpecBuilderTest {

  private val table = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  private val reader = ContinuumWorkflowModel.NodeData(
    description = "Creates a table", title = "Create Table", nodeModel = "org.test.Reader", icon = "<svg>reader</svg>",
    outputs = mapOf("data" to table),
    properties = mapOf("rows" to "[]"),
    propertiesSchema = mapOf("type" to "object", "properties" to mapOf("rows" to mapOf("type" to "string"))),
    propertiesUISchema = mapOf("type" to "VerticalLayout")
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
        "width" to mapOf("type" to "integer"),
        "format" to mapOf("type" to "string")
      ),
      "required" to listOf("smilesColumn")
    )
  )

  private val service: NodeExplorerService = mock<NodeExplorerService>().also {
    whenever(it.getNodeMetadata(any())).thenReturn(null)
    whenever(it.getNodeMetadata("org.test.Reader")).thenReturn(reader)
    whenever(it.getNodeMetadata("org.test.Svg")).thenReturn(svg)
  }
  private val builder = WorkflowSpecBuilder(WorkflowSpecValidator(service), service)

  private fun node(key: String, nodeId: String, properties: Map<String, Any?>? = null) = McpWorkflowSpecNode(key, nodeId, properties)
  private fun edge(from: String, fromPort: String, to: String, toPort: String) = McpWorkflowSpecEdge(from, fromPort, to, toPort)

  @Test
  fun `a valid spec builds the complete workflow`() {
    val result = builder.build(
      "  Render Molecules! ",
      listOf(node("r", "org.test.Reader"), node("s", "org.test.Svg", mapOf("smilesColumn" to "smiles", "width" to 500))),
      listOf(edge("r", "data", "s", "input"))
    )

    assertTrue(result.valid, result.errors.toString())
    assertEquals("render-molecules.cwf", result.fileName)
    val workflow = result.workflow!!
    assertEquals("Render Molecules!", workflow.name)
    assertFalse(workflow.active)
    assertTrue(workflow.id.matches(Regex("[0-9a-f]{64}")), workflow.id)

    val (r, s) = workflow.nodes
    assertEquals("r", r.id)
    assertEquals("BaseNode", r.type)
    assertEquals(WorkflowSpecBuilder.NODE_WIDTH, r.width)
    assertEquals(WorkflowSpecBuilder.NODE_HEIGHT, r.height)
    assertFalse(r.selected)
    assertEquals(false, r.dragging)
    assertEquals(r.position, r.positionAbsolute)
    assertEquals(reader, r.data)
    assertEquals(mapOf("width" to 500, "format" to "svg", "smilesColumn" to "smiles"), s.data.properties)
    assertEquals(svg.propertiesSchema, s.data.propertiesSchema)

    assertEquals(
      listOf(
        ContinuumWorkflowModel.Edge(
          id = "reactflow__edge-rdata-sinput", type = "BaseEdge",
          source = "r", target = "s", sourceHandle = "data", targetHandle = "input"
        )
      ),
      workflow.edges
    )
  }

  @Test
  fun `nodes are laid out by dependency depth`() {
    val result = builder.build(
      "w",
      listOf(
        node("a", "org.test.Reader"),
        node("b", "org.test.Reader"),
        node("s1", "org.test.Svg", mapOf("smilesColumn" to "x")),
        node("s2", "org.test.Svg", mapOf("smilesColumn" to "x")),
        node("s3", "org.test.Svg", mapOf("smilesColumn" to "x"))
      ),
      listOf(edge("a", "data", "s1", "input"), edge("s1", "output", "s2", "input"), edge("b", "data", "s3", "input"))
    )

    val positions = result.workflow!!.nodes.associate { it.id to (it.position.x to it.position.y) }
    assertEquals(0.0 to 0.0, positions["a"])
    assertEquals(0.0 to 160.0, positions["b"])
    assertEquals(330.0 to 0.0, positions["s1"])
    assertEquals(660.0 to 0.0, positions["s2"])
    assertEquals(330.0 to 160.0, positions["s3"])
  }

  @Test
  fun `an invalid spec returns the errors and no workflow`() {
    val result = builder.build("w", listOf(node("s", "org.test.Svg"), node("x", "Missing")), emptyList())

    assertFalse(result.valid)
    assertNull(result.workflow)
    assertNull(result.fileName)
    assertTrue(result.errors.any { it.node == "x" }, result.errors.toString())
    assertTrue(result.errors.any { it.property == "smilesColumn" }, result.errors.toString())
  }

  @Test
  fun `warnings are passed through and each build gets a new id`() {
    val nodes = listOf(node("r", "org.test.Reader"), node("q", "org.test.Reader"))

    val first = builder.build("w", nodes, null)
    val second = builder.build("w", nodes, null)

    assertTrue(first.valid)
    assertTrue(first.warnings.any { it.node == "r" }, first.warnings.toString())
    assertNotEquals(first.workflow!!.id, second.workflow!!.id)
  }

  @Test
  fun `file names are slugs with a fallback`() {
    assertEquals("csv-svg-2.cwf", WorkflowSpecBuilder.fileName("CSV -> SVG (2)"))
    assertEquals("workflow.cwf", WorkflowSpecBuilder.fileName("!!!"))
    assertEquals("a".repeat(60) + ".cwf", WorkflowSpecBuilder.fileName("a".repeat(80)))
  }
}
