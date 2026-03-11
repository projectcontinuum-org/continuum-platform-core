package org.projectcontinuum.core.api.server.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.entity.RegisteredNodeEntity
import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.repository.RegisteredNodeRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeExplorerServiceTest {

  private lateinit var repository: RegisteredNodeRepository
  private lateinit var service: NodeExplorerService
  private val objectMapper = ObjectMapper().apply { registerModule(kotlinModule()) }

  @BeforeEach
  fun setUp() {
    repository = mock()
    service = NodeExplorerService(repository)
  }

  // --- Helper to build test entities ---

  private fun createEntity(
    nodeId: String,
    title: String,
    description: String = "A test node",
    categories: List<String> = emptyList(),
    documentation: String = "# Docs"
  ): RegisteredNodeEntity {
    val manifest = objectMapper.writeValueAsString(
      mapOf(
        "title" to title,
        "description" to description,
        "nodeModel" to nodeId,
        "inputs" to emptyMap<String, Any>(),
        "outputs" to emptyMap<String, Any>(),
        "properties" to emptyMap<String, Any>(),
        "propertiesSchema" to emptyMap<String, Any>(),
        "propertiesUISchema" to emptyMap<String, Any>()
      )
    )
    return RegisteredNodeEntity(
      id = null,
      nodeId = nodeId,
      taskQueue = "TASK_QUEUE",
      workerId = "worker-1",
      featureId = "org.test.feature",
      nodeManifest = manifest,
      documentationMarkdown = documentation,
      categories = objectMapper.writeValueAsString(categories),
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )
  }

  // --- getChildren("") — returns full hierarchical tree ---

  @Test
  fun `getChildren with empty parentId returns hierarchical tree`() {
    val node1 = createEntity("org.test.JointNode", "Joint Node", categories = listOf("Processing"))
    val node2 = createEntity("org.test.FilterNode", "Filter Node", categories = listOf("Processing/KNIME"))
    val node3 = createEntity("org.test.RootNode", "Root Node")
    whenever(repository.findAll()).thenReturn(listOf(node1, node2, node3))

    val result = service.getChildren("")

    // Root level: "Processing" category + uncategorized "Root Node"
    assertEquals(2, result.size)
    assertEquals(NodeExplorerItemType.CATEGORY, result[0].type)
    assertEquals("Processing", result[0].name)
    assertTrue(result[0].hasChildren)
    assertEquals(NodeExplorerItemType.NODE, result[1].type)
    assertEquals("Root Node", result[1].name)

    // Processing has KNIME subcategory + Joint Node
    val processingChildren = result[0].children!!
    assertEquals(2, processingChildren.size)
    assertEquals(NodeExplorerItemType.CATEGORY, processingChildren[0].type)
    assertEquals("KNIME", processingChildren[0].name)
    assertEquals("Processing/KNIME", processingChildren[0].id)
    assertEquals(NodeExplorerItemType.NODE, processingChildren[1].type)
    assertEquals("Joint Node", processingChildren[1].name)

    // KNIME subcategory has Filter Node
    val knimeChildren = processingChildren[0].children!!
    assertEquals(1, knimeChildren.size)
    assertEquals("Filter Node", knimeChildren[0].name)
  }

  @Test
  fun `getChildren with empty parentId places node in multiple categories`() {
    val node = createEntity("org.test.MultiNode", "Multi Node", categories = listOf("Processing", "Transform"))
    whenever(repository.findAll()).thenReturn(listOf(node))

    val result = service.getChildren("")

    assertEquals(2, result.size)
    assertEquals("Processing", result[0].name)
    assertEquals("Transform", result[1].name)
    assertEquals(1, result[0].children!!.size)
    assertEquals("Multi Node", result[0].children!![0].name)
    assertEquals(1, result[1].children!!.size)
    assertEquals("Multi Node", result[1].children!![0].name)
  }

  @Test
  fun `getChildren with empty parentId handles deeply nested category paths`() {
    val node = createEntity("org.test.DeepNode", "Deep Node", categories = listOf("A/B/C"))
    whenever(repository.findAll()).thenReturn(listOf(node))

    val result = service.getChildren("")

    assertEquals(1, result.size)
    assertEquals("A", result[0].name)
    assertEquals("A", result[0].id)
    val bLevel = result[0].children!!
    assertEquals(1, bLevel.size)
    assertEquals("B", bLevel[0].name)
    assertEquals("A/B", bLevel[0].id)
    val cLevel = bLevel[0].children!!
    assertEquals(1, cLevel.size)
    assertEquals("C", cLevel[0].name)
    assertEquals("A/B/C", cLevel[0].id)
    val leafLevel = cLevel[0].children!!
    assertEquals(1, leafLevel.size)
    assertEquals("Deep Node", leafLevel[0].name)
  }

  @Test
  fun `getChildren with empty parentId returns empty when no nodes registered`() {
    whenever(repository.findAll()).thenReturn(emptyList())

    val result = service.getChildren("")

    assertTrue(result.isEmpty())
  }

  @Test
  fun `getChildren with empty parentId sorts categories before nodes alphabetically`() {
    val nodeA = createEntity("org.test.ZNode", "Z Node", categories = listOf("Zebra"))
    val nodeB = createEntity("org.test.ANode", "A Node", categories = listOf("Alpha"))
    val nodeC = createEntity("org.test.RootNode", "Root Node")
    whenever(repository.findAll()).thenReturn(listOf(nodeA, nodeB, nodeC))

    val result = service.getChildren("")

    assertEquals(3, result.size)
    assertEquals("Alpha", result[0].name)
    assertEquals("Zebra", result[1].name)
    assertEquals("Root Node", result[2].name)
  }

  // --- getChildren("Processing") — returns subtree children ---

  @Test
  fun `getChildren with parentId returns children of that category`() {
    val node1 = createEntity("org.test.JointNode", "Joint Node", categories = listOf("Processing"))
    val node2 = createEntity("org.test.FilterNode", "Filter Node", categories = listOf("Processing/KNIME"))
    whenever(repository.findAll()).thenReturn(listOf(node1, node2))

    val result = service.getChildren("Processing")

    assertEquals(2, result.size)
    assertEquals(NodeExplorerItemType.CATEGORY, result[0].type)
    assertEquals("KNIME", result[0].name)
    assertEquals("Processing/KNIME", result[0].id)
    assertEquals(NodeExplorerItemType.NODE, result[1].type)
    assertEquals("Joint Node", result[1].name)
  }

  @Test
  fun `getChildren with nested parentId returns deeper subtree`() {
    val node = createEntity("org.test.AdvNode", "Advanced Node", categories = listOf("Processing/KNIME/Advanced"))
    whenever(repository.findAll()).thenReturn(listOf(node))

    val result = service.getChildren("Processing/KNIME")

    assertEquals(1, result.size)
    assertEquals("Advanced", result[0].name)
    assertEquals("Processing/KNIME/Advanced", result[0].id)
    assertEquals(1, result[0].children!!.size)
    assertEquals("Advanced Node", result[0].children!![0].name)
  }

  @Test
  fun `getChildren with non-existent parentId returns empty`() {
    val node = createEntity("org.test.Node", "Some Node", categories = listOf("Processing"))
    whenever(repository.findAll()).thenReturn(listOf(node))

    val result = service.getChildren("NonExistent")

    assertTrue(result.isEmpty())
  }

  // --- search — returns matching nodes in tree form ---

  @Test
  fun `search returns uncategorized matching node at root`() {
    val node = createEntity("org.test.CreateTableNode", "Create Table", description = "Creates a table")
    whenever(repository.searchNodes("%table%")).thenReturn(listOf(node))

    val result = service.search("table")

    assertEquals(1, result.size)
    assertEquals(NodeExplorerItemType.NODE, result[0].type)
    assertEquals("Create Table", result[0].name)
    assertEquals("org.test.CreateTableNode", result[0].id)
  }

  @Test
  fun `search returns matching nodes nested under their categories`() {
    val node = createEntity("org.test.FilterNode", "Filter Node", categories = listOf("Processing/KNIME"))
    whenever(repository.searchNodes("%filter%")).thenReturn(listOf(node))

    val result = service.search("filter")

    // Result is a tree: Processing → KNIME → Filter Node
    assertEquals(1, result.size)
    assertEquals(NodeExplorerItemType.CATEGORY, result[0].type)
    assertEquals("Processing", result[0].name)
    val knime = result[0].children!!
    assertEquals(1, knime.size)
    assertEquals("KNIME", knime[0].name)
    val nodes = knime[0].children!!
    assertEquals(1, nodes.size)
    assertEquals("Filter Node", nodes[0].name)
    assertEquals(NodeExplorerItemType.NODE, nodes[0].type)
  }

  @Test
  fun `search with blank query returns empty`() {
    val result = service.search("")
    assertTrue(result.isEmpty())
  }

  @Test
  fun `search with whitespace-only query returns empty`() {
    val result = service.search("   ")
    assertTrue(result.isEmpty())
  }

  @Test
  fun `search passes correct ILIKE pattern to repository`() {
    whenever(repository.searchNodes(any())).thenReturn(emptyList())

    service.search("filter")

    verify(repository).searchNodes("%filter%")
  }

  // --- getDocumentation ---

  @Test
  fun `getDocumentation returns markdown for existing node`() {
    whenever(repository.findDocumentationByNodeId("org.test.Node")).thenReturn("# Node Docs\nSome content")

    val result = service.getDocumentation("org.test.Node")

    assertEquals("# Node Docs\nSome content", result)
  }

  @Test
  fun `getDocumentation returns null for non-existing node`() {
    whenever(repository.findDocumentationByNodeId("org.test.NonExistent")).thenReturn(null)

    val result = service.getDocumentation("org.test.NonExistent")

    assertNull(result)
  }
}
