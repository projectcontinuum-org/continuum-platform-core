package org.projectcontinuum.core.api.server.mcp

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode
import org.springframework.ai.mcp.annotation.method.tool.SyncMcpToolMethodCallback
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs the tools through Spring AI's MCP callback to check the result the client actually receives. */
class ContinuumNodeMcpToolsCallbackTest {

  private val service: NodeExplorerService = mock()
  private val tools = ContinuumNodeMcpTools(service)

  private fun call(method: String, args: Map<String, Any>): Pair<Boolean, String> {
    val javaMethod = ContinuumNodeMcpTools::class.java.methods.single { it.name == method }
    val callback = SyncMcpToolMethodCallback(ReturnMode.TEXT, javaMethod, tools)
    val result = callback.apply(mock(), CallToolRequest(method, args))
    return (result.isError == true) to (result.content.single() as TextContent).text
  }

  @Test
  fun `unknown node id is reported as an MCP tool error`() {
    whenever(service.getNodeMetadata("RDKit2SVGNodeModel")).thenReturn(null)

    val (isError, text) = call("getContinuumNodeMetadata", mapOf("nodeId" to "RDKit2SVGNodeModel"))

    assertTrue(isError)
    assertTrue(text.contains("No registered node with id 'RDKit2SVGNodeModel'"), text)
  }

  @Test
  fun `search result omits icon and schemas`() {
    val data = ContinuumWorkflowModel.NodeData(
      description = "Renders molecules as SVG",
      title = "RDKit to SVG",
      nodeModel = "org.test.SvgNode",
      icon = "<svg>ICON</svg>",
      outputs = mapOf("output" to ContinuumWorkflowModel.NodePort("table", "application/x-parquet")),
      propertiesSchema = mapOf("SCHEMA_MARKER" to true)
    )
    whenever(service.search("svg")).thenReturn(
      listOf(NodeExplorerTreeItem(id = "org.test.SvgNode", name = "RDKit to SVG", nodeInfo = data, type = NodeExplorerItemType.NODE))
    )

    val (isError, text) = call("searchContinuumNodes", mapOf("query" to "svg"))

    assertFalse(isError)
    assertTrue(text.contains("\"id\":\"org.test.SvgNode\""), text)
    assertTrue(text.contains("\"outputs\":[\"output\"]"), text)
    assertFalse(text.contains("ICON") || text.contains("SCHEMA_MARKER") || text.contains("null"), text)
  }

  @Test
  fun `known node returns full metadata`() {
    whenever(service.getNodeMetadata("org.test.SvgNode")).thenReturn(
      ContinuumWorkflowModel.NodeData(description = "d", title = "RDKit to SVG", nodeModel = "org.test.SvgNode")
    )

    val (isError, text) = call("getContinuumNodeMetadata", mapOf("nodeId" to "org.test.SvgNode"))

    assertFalse(isError)
    assertEquals(true, text.contains("\"title\":\"RDKit to SVG\""), text)
  }
}
