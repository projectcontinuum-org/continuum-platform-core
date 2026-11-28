package org.projectcontinuum.core.orchestration.config

import com.google.protobuf.ByteString
import io.temporal.api.common.v1.Payload
import io.temporal.common.converter.DataConverter
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Loop fields through the Temporal data converter the orchestration service registers: the mapper from
 * [DataConverterConfig] round-trips `Node.parentNode` and `NodeData.collapsed`, omits them when null (so a stripped
 * node reaches a worker built on an older commons without them), and ignores editor-only node fields.
 */
class DataConverterLoopFieldsTest {

  private val dataConverter: DataConverter = DataConverterConfig().dataConverter()

  private fun nodeData(nodeModel: String, collapsed: Boolean? = null) = ContinuumWorkflowModel.NodeData(
    id = nodeModel,
    description = "d",
    title = "t",
    nodeModel = nodeModel,
    collapsed = collapsed
  )

  private fun node(id: String, type: String, data: ContinuumWorkflowModel.NodeData, parentNode: String? = null) =
    ContinuumWorkflowModel.Node(
      id = id,
      type = type,
      position = ContinuumWorkflowModel.Position(x = 40.0, y = 60.0),
      data = data,
      width = 224,
      height = 100,
      selected = false,
      parentNode = parentNode
    )

  private fun payloadJson(value: Any): String {
    val payload: Optional<Payload> = dataConverter.toPayload(value)
    return payload.get().data.toStringUtf8()
  }

  private fun fromJson(json: String): ContinuumWorkflowModel {
    val template = dataConverter.toPayload(
      ContinuumWorkflowModel(id = "template", name = "template")
    ).get()
    val payload = template.toBuilder()
      .setData(ByteString.copyFromUtf8(json))
      .build()
    return dataConverter.fromPayload(payload, ContinuumWorkflowModel::class.java, ContinuumWorkflowModel::class.java)
  }

  @Test
  fun `parentNode and collapsed round-trip through the Temporal data converter`() {
    val loop = node(
      id = "loop-1",
      type = LoopNode.NODE_TYPE,
      data = nodeData(nodeModel = LoopNode.NODE_MODEL, collapsed = true)
    )
    val member = node(id = "fetch", type = "BaseNode", data = nodeData(nodeModel = "org.test.Step"), parentNode = "loop-1")
    val workflow = ContinuumWorkflowModel(id = "wf", name = "loops.cwf", nodes = listOf(loop, member))

    val payload = dataConverter.toPayload(workflow).get()
    val restored = dataConverter.fromPayload(payload, ContinuumWorkflowModel::class.java, ContinuumWorkflowModel::class.java)

    assertEquals(workflow, restored)
    assertEquals(true, restored.nodes.first { candidate -> candidate.id == "loop-1" }.data.collapsed)
    assertEquals("loop-1", restored.nodes.first { candidate -> candidate.id == "fetch" }.parentNode)
  }

  @Test
  fun `null loop fields are omitted from the payload`() {
    val plainNode = node(id = "a", type = "BaseNode", data = nodeData(nodeModel = "org.test.Step"))

    val json = payloadJson(value = plainNode)

    assertFalse(json.contains("parentNode"), json)
    assertFalse(json.contains("collapsed"), json)
  }

  @Test
  fun `editor-only node fields are ignored by the Temporal mapper`() {
    val json = """
      {"id": "wf", "name": "loops.cwf", "nodes": [{
        "id": "fetch", "type": "BaseNode", "position": {"x": 1.0, "y": 2.0},
        "data": {"description": "d", "title": "t", "nodeModel": "org.test.Step", "editorOnly": 1},
        "width": 224, "height": 100, "selected": false,
        "parentNode": "loop-1", "extent": "parent", "expandParent": true, "zIndex": 3, "style": {"width": 520}
      }], "edges": []}
    """.trimIndent()

    val restored = fromJson(json = json)

    val restoredNode = restored.nodes.single()
    assertEquals("loop-1", restoredNode.parentNode)
    assertNull(restoredNode.data.collapsed)
  }
}
