package org.projectcontinuum.core.api.server.mcp

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContinuumNodeMcpToolsTest {

  private lateinit var nodeExplorerService: NodeExplorerService
  private lateinit var tools: ContinuumNodeMcpTools

  private val port = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")

  private val nodeData = ContinuumWorkflowModel.NodeData(
    description = "Renders molecules as SVG",
    title = "RDKit to SVG",
    nodeModel = "org.test.SvgNode",
    icon = "<svg>large icon</svg>",
    inputs = mapOf("input" to port),
    outputs = mapOf("output" to port),
    properties = mapOf("width" to 300),
    propertiesSchema = mapOf("type" to "object"),
    propertiesUISchema = mapOf("type" to "VerticalLayout")
  )

  @BeforeEach
  fun setUp() {
    nodeExplorerService = mock()
    tools = ContinuumNodeMcpTools(nodeExplorerService)
  }

  @Test
  fun `listContinuumNodeCategories delegates to getChildren with empty parentId`() {
    whenever(nodeExplorerService.getChildren("")).thenReturn(
      listOf(NodeExplorerTreeItem(id = "Processing", name = "Processing", hasChildren = true, type = NodeExplorerItemType.CATEGORY))
    )

    val result = tools.listContinuumNodeCategories(null)

    assertEquals(listOf(McpNodeTreeItem(type = NodeExplorerItemType.CATEGORY, id = "Processing", name = "Processing")), result)
  }

  @Test
  fun `listContinuumNodeCategories returns compact nodes for the given parentId`() {
    whenever(nodeExplorerService.getChildren("RDKit/Rendering")).thenReturn(
      listOf(NodeExplorerTreeItem(id = "org.test.SvgNode", name = "RDKit to SVG", nodeInfo = nodeData, type = NodeExplorerItemType.NODE))
    )

    val result = tools.listContinuumNodeCategories("RDKit/Rendering")

    assertEquals(
      listOf(
        McpNodeTreeItem(
          type = NodeExplorerItemType.NODE,
          id = "org.test.SvgNode",
          name = "RDKit to SVG",
          description = "Renders molecules as SVG",
          inputs = listOf("input"),
          outputs = listOf("output")
        )
      ),
      result
    )
  }

  @Test
  fun `searchContinuumNodes returns the category tree with compact nodes`() {
    val node = NodeExplorerTreeItem(id = "org.test.SvgNode", name = "RDKit to SVG", nodeInfo = nodeData, type = NodeExplorerItemType.NODE)
    val category = NodeExplorerTreeItem(
      id = "RDKit", name = "RDKit", hasChildren = true, type = NodeExplorerItemType.CATEGORY, children = mutableListOf(node)
    )
    whenever(nodeExplorerService.search("svg")).thenReturn(listOf(category))

    val result = tools.searchContinuumNodes("svg")

    assertEquals(1, result.size)
    assertEquals("RDKit", result[0].id)
    val compactNode = result[0].children!!.single()
    assertEquals("org.test.SvgNode", compactNode.id)
    assertEquals("Renders molecules as SVG", compactNode.description)
    assertEquals(listOf("input"), compactNode.inputs)
    assertEquals(listOf("output"), compactNode.outputs)
  }

  @Test
  fun `getContinuumNodeMetadata returns the full node`() {
    whenever(nodeExplorerService.getNodeMetadata("org.test.SvgNode")).thenReturn(nodeData)

    val result = tools.getContinuumNodeMetadata("org.test.SvgNode")

    assertEquals(nodeData, result)
  }

  @Test
  fun `getContinuumNodeMetadata throws a helpful error for an unknown node`() {
    whenever(nodeExplorerService.getNodeMetadata("SvgNode")).thenReturn(null)

    val error = assertThrows<IllegalArgumentException> { tools.getContinuumNodeMetadata("SvgNode") }

    assertTrue(error.message!!.contains("No registered node with id 'SvgNode'"))
    assertTrue(error.message!!.contains("full node id"))
  }

  @Test
  fun `getContinuumNodeDocumentation returns the markdown`() {
    whenever(nodeExplorerService.getDocumentation("org.test.SvgNode")).thenReturn("# RDKit to SVG")

    assertEquals("# RDKit to SVG", tools.getContinuumNodeDocumentation("org.test.SvgNode"))
  }

  @Test
  fun `getContinuumNodeDocumentation says so when a registered node has no docs`() {
    whenever(nodeExplorerService.getDocumentation("org.test.SvgNode")).thenReturn(null)
    whenever(nodeExplorerService.getNodeMetadata("org.test.SvgNode")).thenReturn(nodeData)

    assertTrue(tools.getContinuumNodeDocumentation("org.test.SvgNode").contains("has no documentation"))
  }

  @Test
  fun `getContinuumNodeDocumentation throws for an unknown node`() {
    whenever(nodeExplorerService.getDocumentation("SvgNode")).thenReturn(null)
    whenever(nodeExplorerService.getNodeMetadata("SvgNode")).thenReturn(null)

    val error = assertThrows<IllegalArgumentException> { tools.getContinuumNodeDocumentation("SvgNode") }

    assertTrue(error.message!!.contains("No registered node with id 'SvgNode'"))
  }
}
