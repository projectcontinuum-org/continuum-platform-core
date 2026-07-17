package org.projectcontinuum.core.api.server.service

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.entity.NodeTreeEntryEntity
import org.projectcontinuum.core.api.server.entity.NodeTreeEntryType
import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.repository.jdbc.NodeTreeEntryRepository
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeExplorerServiceTest {

  private lateinit var repository: NodeTreeEntryRepository
  private lateinit var service: NodeExplorerService
  private val objectMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

  private var nextId = 1L

  @BeforeEach
  fun setUp() {
    repository = mock()
    service = NodeExplorerService(repository, objectMapper)
    nextId = 1L
  }

  private fun nextEntryId(): Long = nextId++

  private fun categoryEntity(id: Long, parentId: Long?, name: String): NodeTreeEntryEntity =
    NodeTreeEntryEntity(id = id, parentId = parentId, type = NodeTreeEntryType.CATEGORY, name = name)

  private fun nodeEntity(
    id: Long,
    parentId: Long?,
    nodeId: String,
    title: String,
    description: String = "A test node",
    documentation: String = "# Docs"
  ): NodeTreeEntryEntity {
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
    return NodeTreeEntryEntity(
      id = id,
      parentId = parentId,
      type = NodeTreeEntryType.CONTINUUM_NODE,
      name = title,
      nodeId = nodeId,
      taskQueue = "TASK_QUEUE",
      workerId = "worker-1",
      featureId = "org.test.feature",
      nodeManifest = manifest,
      documentationMarkdown = documentation,
      registeredAt = Instant.now(),
      lastSeenAt = Instant.now()
    )
  }

  private fun stubAncestorLookup(vararg entries: NodeTreeEntryEntity) {
    for (entry in entries) {
      whenever(repository.findById(entry.id!!)).thenReturn(Optional.of(entry))
    }
  }

  // --- getChildren("") — root level ---

  @Test
  fun `getChildren with empty parentId returns only root-level items`() {
    val processing = categoryEntity(nextEntryId(), null, "Processing")
    val rootNode = nodeEntity(nextEntryId(), null, "org.test.RootNode", "Root Node")
    whenever(repository.findRootChildren()).thenReturn(listOf(processing, rootNode))

    val result = service.getChildren("")

    assertEquals(2, result.size)
    assertEquals(NodeExplorerItemType.CATEGORY, result[0].type)
    assertEquals("Processing", result[0].name)
    assertEquals("Processing", result[0].id)
    assertTrue(result[0].hasChildren)
    assertNull(result[0].children)
    assertEquals(NodeExplorerItemType.NODE, result[1].type)
    assertEquals("Root Node", result[1].name)
    assertNull(result[1].children)
  }

  @Test
  fun `getChildren with empty parentId returns empty when no entries registered`() {
    whenever(repository.findRootChildren()).thenReturn(emptyList())

    val result = service.getChildren("")

    assertTrue(result.isEmpty())
  }

  // --- getChildren(parentId) — resolves path then returns immediate children only ---

  @Test
  fun `getChildren with parentId resolves path and returns only immediate children`() {
    val processingId = nextEntryId()
    val knime = categoryEntity(nextEntryId(), processingId, "KNIME")
    val jointNode = nodeEntity(nextEntryId(), processingId, "org.test.JointNode", "Joint Node")
    whenever(repository.findCategoryIdByParentAndName(null, "Processing")).thenReturn(processingId)
    whenever(repository.findChildrenByParentId(processingId)).thenReturn(listOf(knime, jointNode))

    val result = service.getChildren("Processing")

    assertEquals(2, result.size)
    assertEquals(NodeExplorerItemType.CATEGORY, result[0].type)
    assertEquals("KNIME", result[0].name)
    assertEquals("Processing/KNIME", result[0].id)
    assertNull(result[0].children)
    assertEquals(NodeExplorerItemType.NODE, result[1].type)
    assertEquals("Joint Node", result[1].name)
  }

  @Test
  fun `getChildren with nested parentId walks each path segment`() {
    val processingId = nextEntryId()
    val knimeId = nextEntryId()
    val advanced = categoryEntity(nextEntryId(), knimeId, "Advanced")
    whenever(repository.findCategoryIdByParentAndName(null, "Processing")).thenReturn(processingId)
    whenever(repository.findCategoryIdByParentAndName(processingId, "KNIME")).thenReturn(knimeId)
    whenever(repository.findChildrenByParentId(knimeId)).thenReturn(listOf(advanced))

    val result = service.getChildren("Processing/KNIME")

    assertEquals(1, result.size)
    assertEquals("Advanced", result[0].name)
    assertEquals("Processing/KNIME/Advanced", result[0].id)
    assertNull(result[0].children)
  }

  @Test
  fun `getChildren with non-existent parentId returns empty without querying children`() {
    whenever(repository.findCategoryIdByParentAndName(null, "NonExistent")).thenReturn(null)

    val result = service.getChildren("NonExistent")

    assertTrue(result.isEmpty())
  }

  // --- search — returns matching nodes nested under reconstructed ancestor chain ---

  @Test
  fun `search returns uncategorized matching node at root`() {
    val node = nodeEntity(nextEntryId(), null, "org.test.CreateTableNode", "Create Table", description = "Creates a table")
    whenever(repository.searchNodes("%table%")).thenReturn(listOf(node))

    val result = service.search("table")

    assertEquals(1, result.size)
    assertEquals(NodeExplorerItemType.NODE, result[0].type)
    assertEquals("Create Table", result[0].name)
    assertEquals("org.test.CreateTableNode", result[0].id)
  }

  @Test
  fun `search returns matching nodes nested under their reconstructed category chain`() {
    val processing = categoryEntity(nextEntryId(), null, "Processing")
    val knime = categoryEntity(nextEntryId(), processing.id, "KNIME")
    val filterNode = nodeEntity(nextEntryId(), knime.id, "org.test.FilterNode", "Filter Node")
    stubAncestorLookup(processing, knime)
    whenever(repository.searchNodes("%filter%")).thenReturn(listOf(filterNode))

    val result = service.search("filter")

    assertEquals(1, result.size)
    assertEquals(NodeExplorerItemType.CATEGORY, result[0].type)
    assertEquals("Processing", result[0].name)
    val knimeChildren = result[0].children!!
    assertEquals(1, knimeChildren.size)
    assertEquals("KNIME", knimeChildren[0].name)
    val nodes = knimeChildren[0].children!!
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
    whenever(repository.findByNodeId("org.test.Node")).thenReturn(
      nodeEntity(
        id = nextEntryId(),
        parentId = null,
        nodeId = "org.test.Node",
        title = "Node",
        documentation = "# Node Docs\nSome content"
      )
    )

    val result = service.getDocumentation("org.test.Node")

    assertEquals("# Node Docs\nSome content", result)
  }

  @Test
  fun `getDocumentation returns null for non-existing node`() {
    whenever(repository.findByNodeId("org.test.NonExistent")).thenReturn(null)

    val result = service.getDocumentation("org.test.NonExistent")

    assertNull(result)
  }

  // --- getTaskQueues ---

  @Test
  fun `getTaskQueues maps node ids to their task queue despite multiple placements`() {
    val nodeAtRoot = nodeEntity(nextEntryId(), null, "org.test.MultiNode", "Multi Node")
    val nodeAtCategory = nodeEntity(nextEntryId(), 99L, "org.test.MultiNode", "Multi Node")
    whenever(repository.findByNodeIdIn(listOf("org.test.MultiNode"))).thenReturn(listOf(nodeAtRoot, nodeAtCategory))

    val result = service.getTaskQueues(listOf("org.test.MultiNode"))

    assertEquals(mapOf("org.test.MultiNode" to "TASK_QUEUE"), result)
  }

  @Test
  fun `getTaskQueues with empty input returns empty map without querying`() {
    val result = service.getTaskQueues(emptyList())

    assertTrue(result.isEmpty())
  }
}
