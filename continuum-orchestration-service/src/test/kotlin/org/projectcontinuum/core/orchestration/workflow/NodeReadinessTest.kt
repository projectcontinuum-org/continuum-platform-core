package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.EdgeStatus
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.orchestration.workflow.fixtures.WorkflowFixtures
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeReadinessTest {

  private val diamond = "/test-workflows/diamond-4node.cwf.json"
  private val diamondJoin = "/test-workflows/diamond-join-4node.cwf.json"

  private fun portData(status: PortDataStatus, data: String) =
    PortData(status = status, contentType = "text/plain", tableSpec = emptyList(), data = data)

  private val successOutputs = mapOf("out-1" to portData(status = PortDataStatus.SUCCESS, data = "x"))
  private val errorOutputs = mapOf("\$error" to portData(status = PortDataStatus.FAILED, data = "e"))

  private fun skippedIds(
    model: ContinuumWorkflowModel,
    outputs: Map<String, Map<String, PortData>>,
    errors: Map<String, Map<String, PortData>>
  ) = NodeReadiness.getNodesToSkip(continuumWorkflow = model, nodeOutputMap = outputs, nodeErrorsMap = errors)
    .map { it.id }

  private fun readyIds(
    model: ContinuumWorkflowModel,
    outputs: Map<String, Map<String, PortData>>,
    errors: Map<String, Map<String, PortData>>
  ) = NodeReadiness.getReadyNodes(continuumWorkflow = model, nodeOutputMap = outputs, nodeErrorsMap = errors)
    .map { it.id }

  private fun edgeStatusesById(
    model: ContinuumWorkflowModel,
    outputs: Map<String, Map<String, PortData>>,
    errors: Map<String, Map<String, PortData>>
  ) = NodeReadiness.getEdgeStatuses(continuumWorkflow = model, nodeOutputMap = outputs, nodeErrorsMap = errors)
    .associate { (edge, status) -> edge.id to status }

  @Test
  fun `root failure skips all descendants transitively and readiness is pure`() {
    val model = WorkflowFixtures.loadWorkflow(diamond)
    val errors = mapOf("A" to errorOutputs)

    assertEquals(listOf("B", "C", "D"), skippedIds(model = model, outputs = emptyMap(), errors = errors))
    assertEquals(emptyList(), readyIds(model = model, outputs = emptyMap(), errors = errors))
    // Pure: model untouched
    assertEquals(listOf(null, null, null, null), model.nodes.map { it.data.status })
  }

  @Test
  fun `failed branch skips the join while the independent sibling stays ready`() {
    val model = WorkflowFixtures.loadWorkflow(diamond)
    val outputs = mapOf("A" to successOutputs, "B" to successOutputs)
    val errors = mapOf("C" to errorOutputs)

    assertEquals(listOf("D"), skippedIds(model = model, outputs = outputs, errors = errors))
    assertEquals(emptyList(), readyIds(model = model, outputs = outputs, errors = errors))
    assertEquals(
      mapOf(
        "edge-A-B" to EdgeStatus.DELIVERED,
        "edge-A-C" to EdgeStatus.DELIVERED,
        "edge-B-D" to EdgeStatus.DELIVERED,
        "edge-C-D" to EdgeStatus.BROKEN
      ),
      edgeStatusesById(model = model, outputs = outputs, errors = errors)
    )
  }

  @Test
  fun `already skipped parent propagates and skipped nodes are not skipped again`() {
    val model = WorkflowFixtures.loadWorkflow(diamond)
    model.nodes.first { it.id == "B" }.data.status = NodeStatus.SKIPPED
    val outputs = mapOf("A" to successOutputs)

    assertEquals(listOf("D"), skippedIds(model = model, outputs = outputs, errors = emptyMap()))
    assertEquals(listOf("C"), readyIds(model = model, outputs = outputs, errors = emptyMap()))
    assertEquals(
      mapOf("edge-A-B" to EdgeStatus.DELIVERED, "edge-A-C" to EdgeStatus.DELIVERED, "edge-B-D" to EdgeStatus.BROKEN),
      edgeStatusesById(model = model, outputs = outputs, errors = emptyMap())
    )
  }

  @Test
  fun `a parent that succeeds without producing the connected port skips the child`() {
    val model = WorkflowFixtures.loadWorkflow(diamondJoin)
    // B is wired to D through out-2 but only produced out-1
    val outputs = mapOf("A" to successOutputs, "B" to successOutputs)

    assertEquals(listOf("D"), skippedIds(model = model, outputs = outputs, errors = emptyMap()))
    assertEquals(listOf("C"), readyIds(model = model, outputs = outputs, errors = emptyMap()))
    // B delivered out-1 to nobody; the edge B -> D waits on out-2, which B never produced
    assertEquals(
      mapOf("edge-A-B" to EdgeStatus.DELIVERED, "edge-A-C" to EdgeStatus.DELIVERED, "edge-B-D" to EdgeStatus.BROKEN),
      edgeStatusesById(model = model, outputs = outputs, errors = emptyMap())
    )
  }

  @Test
  fun `edges from a parent that has not finished yet have no status`() {
    val model = WorkflowFixtures.loadWorkflow(diamond)
    val outputs = mapOf("A" to successOutputs)

    assertEquals(emptyList(), skippedIds(model = model, outputs = outputs, errors = emptyMap()))
    assertEquals(listOf("B", "C"), readyIds(model = model, outputs = outputs, errors = emptyMap()))
    assertEquals(
      mapOf("edge-A-B" to EdgeStatus.DELIVERED, "edge-A-C" to EdgeStatus.DELIVERED),
      edgeStatusesById(model = model, outputs = outputs, errors = emptyMap())
    )
  }
}
