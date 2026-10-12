package org.projectcontinuum.core.api.server.mcp

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContinuumNodeMcpToolsTest {

  private lateinit var nodeExplorerService: NodeExplorerService
  private lateinit var tools: ContinuumNodeMcpTools

  @BeforeEach
  fun setUp() {
    nodeExplorerService = mock()
    tools = ContinuumNodeMcpTools(nodeExplorerService)
  }

  @Test
  fun `listContinuumNodeCategories delegates to getChildren with empty parentId`() {
    val items = listOf(
      NodeExplorerTreeItem(id = "Processing", name = "Processing", hasChildren = true, type = NodeExplorerItemType.CATEGORY)
    )
    whenever(nodeExplorerService.getChildren("")).thenReturn(items)

    val result = tools.listContinuumNodeCategories(null)

    assertEquals(items, result)
  }

  @Test
  fun `listContinuumNodeCategories delegates to getChildren with given parentId`() {
    val items = listOf(
      NodeExplorerTreeItem(id = "Processing/KNIME", name = "KNIME", hasChildren = true, type = NodeExplorerItemType.CATEGORY)
    )
    whenever(nodeExplorerService.getChildren("Processing")).thenReturn(items)

    val result = tools.listContinuumNodeCategories("Processing")

    assertEquals(items, result)
  }

  @Test
  fun `searchContinuumNodes delegates to search`() {
    val items = listOf(
      NodeExplorerTreeItem(id = "org.test.FilterNode", name = "Filter Node", type = NodeExplorerItemType.NODE)
    )
    whenever(nodeExplorerService.search("filter")).thenReturn(items)

    val result = tools.searchContinuumNodes("filter")

    assertEquals(items, result)
  }

  @Test
  fun `getContinuumNodeMetadata delegates to getNodeMetadata`() {
    val nodeData = ContinuumWorkflowModel.NodeData(
      description = "A test node",
      title = "Test Node",
      nodeModel = "org.test.Node"
    )
    whenever(nodeExplorerService.getNodeMetadata("org.test.Node")).thenReturn(nodeData)

    val result = tools.getContinuumNodeMetadata("org.test.Node")

    assertEquals(nodeData, result)
  }

  @Test
  fun `getContinuumNodeMetadata returns null for unknown node`() {
    whenever(nodeExplorerService.getNodeMetadata("org.test.Unknown")).thenReturn(null)

    val result = tools.getContinuumNodeMetadata("org.test.Unknown")

    assertNull(result)
  }
}
