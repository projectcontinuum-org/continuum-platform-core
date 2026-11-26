package org.projectcontinuum.core.commons.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.constant.TaskQueues
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * Loop fields of the workflow model: `Node.parentNode` and `NodeData.collapsed` are optional, a workflow
 * without them parses and serializes as before, a workflow with them round-trips unchanged, and editor-only React Flow
 * fields are ignored. Uses the Jackson 3 Kotlin mapper, the family the api-server reads HTTP bodies with.
 */
class ContinuumWorkflowModelLoopFieldsTest {

  private val objectMapper = jacksonObjectMapper()

  private fun nodeJson(
    id: String,
    type: String = "BaseNode",
    nodeModel: String = "org.test.Step",
    extraNodeFields: String = "",
    extraDataFields: String = ""
  ): String = """
    {
      "id": "$id",
      "type": "$type",
      "position": {"x": 40.0, "y": 60.0},
      "data": {
        "id": "$nodeModel",
        "description": "d",
        "title": "t",
        "nodeModel": "$nodeModel",
        "properties": {}$extraDataFields
      },
      "width": 224,
      "height": 100,
      "selected": false$extraNodeFields
    }
  """.trimIndent()

  private fun workflowJson(vararg nodes: String): String = """
    {"id": "wf", "name": "loops.cwf", "active": true, "nodes": [${nodes.joinToString(separator = ",")}], "edges": []}
  """.trimIndent()

  private fun nodeTree(json: String, nodeId: String): JsonNode =
    objectMapper.readTree(json).get("nodes").first { node -> node.get("id").asString() == nodeId }

  @Test
  fun `a workflow without the loop fields parses with null values and serializes without them`() {
    val json = workflowJson(nodeJson(id = "a"))

    val workflow = objectMapper.readValue<ContinuumWorkflowModel>(json)
    val node = workflow.nodes.single()
    assertNull(node.parentNode)
    assertNull(node.data.collapsed)

    val serializedNode = nodeTree(json = objectMapper.writeValueAsString(workflow), nodeId = "a")
    assertFalse(serializedNode.has("parentNode"), "a null parentNode is omitted, so loop-free workflows are unchanged")
    assertFalse(serializedNode.get("data").has("collapsed"), "a null collapsed flag is omitted")
  }

  @Test
  fun `a workflow with a loop and a member round-trips parentNode and collapsed unchanged`() {
    val loopJson = nodeJson(
      id = "loop-1",
      type = LoopNode.NODE_TYPE,
      nodeModel = LoopNode.NODE_MODEL,
      extraDataFields = """, "collapsed": true"""
    )
    val memberJson = nodeJson(id = "fetch", extraNodeFields = """, "parentNode": "loop-1"""")
    val json = workflowJson(loopJson, memberJson)

    val workflow = objectMapper.readValue<ContinuumWorkflowModel>(json)
    val loop = workflow.nodes.first { node -> node.id == "loop-1" }
    val member = workflow.nodes.first { node -> node.id == "fetch" }
    assertEquals(true, loop.data.collapsed)
    assertNull(loop.parentNode)
    assertEquals("loop-1", member.parentNode)
    assertNull(member.data.collapsed)
    assertTrue(LoopNode.isLoop(node = loop))
    assertFalse(LoopNode.isLoop(node = member))

    val serialized = objectMapper.writeValueAsString(workflow)
    assertEquals(
      objectMapper.readTree("true"),
      nodeTree(json = serialized, nodeId = "loop-1").get("data").get("collapsed")
    )
    assertEquals(
      objectMapper.readTree("\"loop-1\""),
      nodeTree(json = serialized, nodeId = "fetch").get("parentNode")
    )
    assertEquals(workflow, objectMapper.readValue<ContinuumWorkflowModel>(serialized), "a second round trip is stable")
  }

  @Test
  fun `editor-only React Flow fields on a node and in its data are ignored`() {
    val memberJson = nodeJson(
      id = "fetch",
      extraNodeFields = """, "parentNode": "loop-1", "extent": "parent", "expandParent": true, "zIndex": 3, "style": {"width": 520}""",
      extraDataFields = """, "someFutureEditorFlag": {"nested": [1, 2]}"""
    )
    val json = workflowJson(memberJson)
    // Strict mapper: the @JsonIgnoreProperties on Node and NodeData decide, not the mapper default
    val strictMapper = jacksonMapperBuilder()
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build()

    val workflow = strictMapper.readValue<ContinuumWorkflowModel>(json)

    assertEquals("loop-1", workflow.nodes.single().parentNode)
  }

  @Test
  fun `loop constants match the frozen contract`() {
    assertEquals("Loop", LoopNode.NODE_TYPE)
    assertEquals("org.projectcontinuum.core.builtin.Loop", LoopNode.NODE_MODEL)
    assertEquals("Flow Control", LoopNode.CATEGORY)
    assertEquals("\$builtin", TaskQueues.BUILTIN_NO_ACTIVITY)
  }
}
