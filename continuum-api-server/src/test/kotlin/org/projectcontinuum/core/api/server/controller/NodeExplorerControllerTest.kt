package org.projectcontinuum.core.api.server.controller

import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.model.NodeExplorerItemType
import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(NodeExplorerController::class)
class NodeExplorerControllerTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @MockitoBean
  private lateinit var nodeExplorerService: NodeExplorerService

  @Test
  fun `GET children returns hierarchical tree`() {
    whenever(nodeExplorerService.getChildren("")).thenReturn(
      listOf(
        NodeExplorerTreeItem(
          id = "Processing",
          name = "Processing",
          hasChildren = true,
          type = NodeExplorerItemType.CATEGORY,
          children = mutableListOf(
            NodeExplorerTreeItem(
              id = "org.test.JointNode",
              name = "Joint Node",
              nodeInfo = ContinuumWorkflowModel.NodeData(
                title = "Joint Node",
                description = "Joins inputs",
                nodeModel = "org.test.JointNode"
              ),
              type = NodeExplorerItemType.NODE
            )
          )
        )
      )
    )

    mockMvc.perform(get("/api/v1/node-explorer/children"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].id").value("Processing"))
      .andExpect(jsonPath("$[0].type").value("CATEGORY"))
      .andExpect(jsonPath("$[0].hasChildren").value(true))
      .andExpect(jsonPath("$[0].children.length()").value(1))
      .andExpect(jsonPath("$[0].children[0].id").value("org.test.JointNode"))
      .andExpect(jsonPath("$[0].children[0].name").value("Joint Node"))
      .andExpect(jsonPath("$[0].children[0].type").value("NODE"))
      .andExpect(jsonPath("$[0].children[0].nodeInfo.title").value("Joint Node"))
  }

  @Test
  fun `GET children with parentId returns subtree children`() {
    val nodeData = ContinuumWorkflowModel.NodeData(
      title = "Joint Node",
      description = "Joins inputs",
      nodeModel = "org.test.JointNode",
      inputs = emptyMap(),
      outputs = emptyMap()
    )
    whenever(nodeExplorerService.getChildren("Processing")).thenReturn(
      listOf(
        NodeExplorerTreeItem(
          id = "org.test.JointNode",
          name = "Joint Node",
          nodeInfo = nodeData,
          hasChildren = false,
          type = NodeExplorerItemType.NODE
        )
      )
    )

    mockMvc.perform(get("/api/v1/node-explorer/children").param("parentId", "Processing"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].id").value("org.test.JointNode"))
      .andExpect(jsonPath("$[0].name").value("Joint Node"))
      .andExpect(jsonPath("$[0].type").value("NODE"))
      .andExpect(jsonPath("$[0].nodeInfo.title").value("Joint Node"))
      .andExpect(jsonPath("$[0].nodeInfo.description").value("Joins inputs"))
  }

  @Test
  fun `GET children with no results returns empty array`() {
    whenever(nodeExplorerService.getChildren("NonExistent")).thenReturn(emptyList())

    mockMvc.perform(get("/api/v1/node-explorer/children").param("parentId", "NonExistent"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(0))
  }

  @Test
  fun `GET search returns matching nodes in tree structure`() {
    whenever(nodeExplorerService.search("table")).thenReturn(
      listOf(
        NodeExplorerTreeItem(
          id = "org.test.CreateTableNode",
          name = "Create Table",
          nodeInfo = ContinuumWorkflowModel.NodeData(
            title = "Create Table",
            description = "Creates a table",
            nodeModel = "org.test.CreateTableNode"
          ),
          hasChildren = false,
          type = NodeExplorerItemType.NODE
        )
      )
    )

    mockMvc.perform(get("/api/v1/node-explorer/search").param("query", "table"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].id").value("org.test.CreateTableNode"))
      .andExpect(jsonPath("$[0].name").value("Create Table"))
      .andExpect(jsonPath("$[0].type").value("NODE"))
  }

  @Test
  fun `GET search with no matches returns empty array`() {
    whenever(nodeExplorerService.search("nonexistent")).thenReturn(emptyList())

    mockMvc.perform(get("/api/v1/node-explorer/search").param("query", "nonexistent"))
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(0))
  }

  @Test
  fun `GET search without query param returns 400`() {
    mockMvc.perform(get("/api/v1/node-explorer/search"))
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `GET documentation returns markdown for existing node`() {
    whenever(nodeExplorerService.getDocumentation("org.test.Node"))
      .thenReturn("# Node Documentation\nThis node does things.")

    mockMvc.perform(get("/api/v1/node-explorer/nodes/org.test.Node/documentation"))
      .andExpect(status().isOk)
      .andExpect(content().contentTypeCompatibleWith("text/markdown"))
      .andExpect(content().string("# Node Documentation\nThis node does things."))
  }

  @Test
  fun `GET documentation returns 404 for non-existing node`() {
    whenever(nodeExplorerService.getDocumentation("org.test.NonExistent"))
      .thenReturn(null)

    mockMvc.perform(get("/api/v1/node-explorer/nodes/org.test.NonExistent/documentation"))
      .andExpect(status().isNotFound)
  }
}
