package org.projectcontinuum.core.api.server.mcp

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import kotlin.test.assertEquals

class ContinuumNodeMcpResourcesTest {

  private lateinit var nodeExplorerService: NodeExplorerService
  private lateinit var resources: ContinuumNodeMcpResources

  @BeforeEach
  fun setUp() {
    nodeExplorerService = mock()
    resources = ContinuumNodeMcpResources(nodeExplorerService)
  }

  @Test
  fun `getNodeDocumentation returns markdown for existing node`() {
    whenever(nodeExplorerService.getDocumentation("org.test.Node")).thenReturn("# Node Docs\nSome content")

    val result = resources.getNodeDocumentation("org.test.Node")

    assertEquals("# Node Docs\nSome content", result)
  }

  @Test
  fun `getNodeDocumentation returns a fallback message for unknown node`() {
    whenever(nodeExplorerService.getDocumentation("org.test.NonExistent")).thenReturn(null)

    val result = resources.getNodeDocumentation("org.test.NonExistent")

    assertEquals("No documentation available for node 'org.test.NonExistent'.", result)
  }
}
