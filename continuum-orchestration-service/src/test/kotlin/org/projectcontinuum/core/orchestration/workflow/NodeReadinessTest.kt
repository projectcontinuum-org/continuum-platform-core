package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.orchestration.workflow.fixtures.WorkflowFixtures
import kotlin.test.Test
import kotlin.test.assertEquals

class NodeReadinessTest {

  private val diamond = "/test-workflows/diamond-4node.cwf.json"

  private fun port() = mapOf(
    "out-1" to PortData(status = PortDataStatus.SUCCESS, contentType = "text/plain", tableSpec = emptyList(), data = "x")
  )

  private val error = mapOf(
    "\$error" to PortData(status = PortDataStatus.FAILED, contentType = "text/plain", tableSpec = emptyList(), data = "e")
  )

  @Test
  fun `root failure skips all descendants transitively and readiness is pure`() {
    val model = WorkflowFixtures.loadWorkflow(diamond)
    val errors = mapOf("A" to error)

    val skip = NodeReadiness.getNodesToSkip(model, emptyMap(), errors)
    assertEquals(listOf("B", "C", "D"), skip.map { it.id })
    assertEquals(emptyList(), NodeReadiness.getReadyNodes(model, emptyMap(), errors).map { it.id })
    // Pure: model untouched
    assertEquals(listOf(null, null, null, null), model.nodes.map { it.data.status })
  }

  @Test
  fun `failed branch skips the join while the independent sibling stays ready`() {
    val model = WorkflowFixtures.loadWorkflow(diamond)
    val outputs = mapOf("A" to port(), "B" to port())
    val errors = mapOf("C" to error)

    assertEquals(listOf("D"), NodeReadiness.getNodesToSkip(model, outputs, errors).map { it.id })
    assertEquals(emptyList(), NodeReadiness.getReadyNodes(model, outputs, errors).map { it.id })
  }

  @Test
  fun `already skipped parent propagates and skipped nodes are not skipped again`() {
    val model = WorkflowFixtures.loadWorkflow(diamond)
    model.nodes.first { it.id == "B" }.data.status = NodeStatus.SKIPPED
    val outputs = mapOf("A" to port())

    assertEquals(listOf("D"), NodeReadiness.getNodesToSkip(model, outputs, emptyMap()).map { it.id })
    assertEquals(listOf("C"), NodeReadiness.getReadyNodes(model, outputs, emptyMap()).map { it.id })
  }
}
