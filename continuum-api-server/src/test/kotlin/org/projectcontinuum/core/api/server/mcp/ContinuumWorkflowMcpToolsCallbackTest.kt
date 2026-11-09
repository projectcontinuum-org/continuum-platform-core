package org.projectcontinuum.core.api.server.mcp

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import io.modelcontextprotocol.spec.McpSchema.TextContent
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode
import org.springframework.ai.mcp.annotation.method.tool.SyncMcpToolMethodCallback
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs the workflow tools through Spring AI's MCP callback, with arguments as a client sends them. */
class ContinuumWorkflowMcpToolsCallbackTest {

  private val service: NodeExplorerService = mock()
  private val validator = WorkflowSpecValidator(service)
  private val tools = ContinuumWorkflowMcpTools(validator, WorkflowSpecBuilder(validator, service))
  private val method = toolMethod("validateContinuumWorkflow")

  private fun toolMethod(name: String) = ContinuumWorkflowMcpTools::class.java.methods.single { it.name == name }

  private fun call(args: Map<String, Any>, tool: String = "validateContinuumWorkflow"): Pair<Boolean, String> {
    val callback = SyncMcpToolMethodCallback(ReturnMode.TEXT, toolMethod(tool), tools)
    val result = callback.apply(mock(), CallToolRequest(tool, args))
    return (result.isError == true) to (result.content.single() as TextContent).text
  }

  @Test
  fun `buildContinuumWorkflow returns the verdict first and the full workflow json`() {
    whenever(service.getNodeMetadata("org.test.Svg")).thenReturn(
      ContinuumWorkflowModel.NodeData(
        description = "d", title = "SVG", nodeModel = "org.test.Svg", icon = "<svg/>",
        propertiesUISchema = mapOf("type" to "VerticalLayout")
      )
    )

    val (isError, text) = call(
      mapOf("name" to "My Flow", "nodes" to listOf(mapOf("key" to "s", "nodeId" to "org.test.Svg"))),
      tool = "buildContinuumWorkflow"
    )

    assertFalse(isError, text)
    assertTrue(text.startsWith("{\"valid\":true,\"fileName\":\"my-flow.cwf\""), text)
    for (field in listOf("\"selected\":false", "\"type\":\"BaseNode\"", "\"icon\":\"<svg/>\"", "\"propertiesUISchema\"", "\"active\":false")) {
      assertTrue(text.contains(field), "$field missing from $text")
    }
  }

  @Test
  fun `buildContinuumWorkflow omits the workflow when invalid`() {
    val (isError, text) = call(
      mapOf("name" to "w", "nodes" to listOf(mapOf("key" to "x", "nodeId" to "Missing"))),
      tool = "buildContinuumWorkflow"
    )

    assertFalse(isError, text)
    assertTrue(text.contains("\"valid\":false"), text)
    assertFalse(text.contains("\"workflow\""), text)
  }

  @Test
  fun `input schema describes the spec nodes and edges`() {
    val schema = McpJsonSchemaGenerator.generateForMethodInput(method)

    for (field in listOf("\"nodes\"", "\"edges\"", "\"key\"", "\"nodeId\"", "\"fromPort\"", "\"toPort\"")) {
      assertTrue(schema.contains(field), "$field missing from $schema")
    }
  }

  @Test
  fun `invalid workflow is a normal result listing every error`() {
    whenever(service.getNodeMetadata("org.test.Svg")).thenReturn(
      ContinuumWorkflowModel.NodeData(
        description = "d", title = "SVG", nodeModel = "org.test.Svg",
        inputs = mapOf("input" to ContinuumWorkflowModel.NodePort("table", "application/x-parquet")),
        propertiesSchema = mapOf("type" to "object", "properties" to mapOf("width" to mapOf("type" to "integer")))
      )
    )

    val (isError, text) = call(
      mapOf(
        "name" to "w",
        "nodes" to listOf(
          mapOf("key" to "s", "nodeId" to "org.test.Svg", "properties" to mapOf("width" to "wide")),
          mapOf("key" to "x", "nodeId" to "Missing")
        ),
        "edges" to listOf(mapOf("from" to "x", "fromPort" to "out", "to" to "s", "toPort" to "input"))
      )
    )

    assertFalse(isError, text)
    assertTrue(text.contains("\"valid\":false"), text)
    assertTrue(text.contains("no registered node with id 'Missing'"), text)
    assertTrue(text.contains("\"property\":\"width\""), text)
  }

  @Test
  fun `missing edges argument is accepted`() {
    whenever(service.getNodeMetadata("org.test.Svg")).thenReturn(
      ContinuumWorkflowModel.NodeData(description = "d", title = "SVG", nodeModel = "org.test.Svg")
    )

    val (isError, text) = call(mapOf("name" to "w", "nodes" to listOf(mapOf("key" to "s", "nodeId" to "org.test.Svg"))))

    assertFalse(isError, text)
    assertTrue(text.contains("\"valid\":true"), text)
  }
}
