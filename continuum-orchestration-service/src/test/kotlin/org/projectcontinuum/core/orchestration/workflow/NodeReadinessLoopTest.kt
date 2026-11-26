package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.EdgeStatus
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopWorkflowBuilder
import org.projectcontinuum.core.orchestration.workflow.fixtures.WorkflowFixtures
import org.projectcontinuum.core.orchestration.workflow.loop.LoopReadinessGate
import org.projectcontinuum.core.orchestration.workflow.loop.LoopTopology
import kotlin.test.Test
import kotlin.test.assertEquals

/** Readiness with loops (members wait for their loop, consumers wait for the loop to finish) through [LoopReadinessGate]. */
class NodeReadinessLoopTest {

  private fun portData(status: PortDataStatus) =
    PortData(status = status, contentType = "text/plain", tableSpec = emptyList(), data = "x")

  private val delivered = mapOf("out" to portData(status = PortDataStatus.SUCCESS))
  private val failed = mapOf("\$error" to portData(status = PortDataStatus.FAILED))

  /** seed -> [fetch -> parse] -> write */
  private val model = LoopWorkflowBuilder()
    .source(id = "seed")
    .loop(id = "L")
    .step(id = "fetch", parent = "L")
    .step(id = "parse", parent = "L")
    .step(id = "write")
    .edge(from = "seed", to = "fetch")
    .edge(from = "fetch", to = "parse")
    .edge(from = "parse", to = "write")
    .build()

  private val topology = LoopTopology.of(workflow = model)

  private fun gate(
    active: Set<String> = emptySet(),
    finished: Set<String> = emptySet(),
    failedOrSkipped: Set<String> = emptySet()
  ) = LoopReadinessGate(
    topology = topology,
    activeLoopIds = active,
    finishedLoopIds = finished,
    failedOrSkippedLoopIds = failedOrSkipped
  )

  private fun readyIds(outputs: Map<String, Map<String, PortData>>, errors: Map<String, Map<String, PortData>> = emptyMap(), loopGate: LoopReadinessGate) =
    NodeReadiness.getReadyNodes(continuumWorkflow = model, nodeOutputMap = outputs, nodeErrorsMap = errors, loopGate = loopGate)
      .map { node -> node.id }

  private fun skippedIds(outputs: Map<String, Map<String, PortData>>, errors: Map<String, Map<String, PortData>> = emptyMap(), loopGate: LoopReadinessGate) =
    NodeReadiness.getNodesToSkip(continuumWorkflow = model, nodeOutputMap = outputs, nodeErrorsMap = errors, loopGate = loopGate)
      .map { node -> node.id }

  private fun edgeStatuses(outputs: Map<String, Map<String, PortData>>, loopGate: LoopReadinessGate) =
    NodeReadiness.getEdgeStatuses(continuumWorkflow = model, nodeOutputMap = outputs, nodeErrorsMap = emptyMap(), loopGate = loopGate)
      .associate { (edge, status) -> edge.id to status }

  @Test
  fun `a member is not ready while its loop is not in an active iteration`() {
    val outputs = mapOf("seed" to delivered)

    assertEquals(emptyList(), readyIds(outputs = outputs, loopGate = gate()), "the loop box itself is never ready: it has no activity")
    assertEquals(listOf("fetch"), readyIds(outputs = outputs, loopGate = gate(active = setOf("L"))))
  }

  @Test
  fun `a consumer of a member is not ready until the loop has finished`() {
    val outputs = mapOf("seed" to delivered, "fetch" to delivered, "parse" to delivered)

    assertEquals(emptyList(), readyIds(outputs = outputs, loopGate = gate(active = setOf("L"))).filter { id -> id == "write" })
    assertEquals(null, edgeStatuses(outputs = outputs, loopGate = gate(active = setOf("L")))["parse-write"])
    assertEquals(listOf("write"), readyIds(outputs = outputs, loopGate = gate(finished = setOf("L"))).filter { id -> id == "write" })
    assertEquals(EdgeStatus.DELIVERED, edgeStatuses(outputs = outputs, loopGate = gate(finished = setOf("L")))["parse-write"])
  }

  @Test
  fun `an edge leaving a failed or skipped loop is broken and skips the consumer`() {
    val outputs = mapOf("seed" to delivered, "fetch" to delivered, "parse" to delivered)
    val failedGate = gate(finished = setOf("L"), failedOrSkipped = setOf("L"))

    assertEquals(EdgeStatus.BROKEN, edgeStatuses(outputs = outputs, loopGate = failedGate)["parse-write"])
    assertEquals(listOf("write"), skippedIds(outputs = outputs, loopGate = failedGate))
  }

  @Test
  fun `a failing member does not skip the outside consumer before the loop has decided`() {
    val outputs = mapOf("seed" to delivered, "fetch" to delivered)
    val errors = mapOf("parse" to failed)

    assertEquals(emptyList(), skippedIds(outputs = outputs, errors = errors, loopGate = gate(active = setOf("L"))))
  }

  @Test
  fun `members of an inactive loop are never skipped by the generic rules`() {
    val errors = mapOf("seed" to failed)

    val skipped = skippedIds(outputs = emptyMap(), errors = errors, loopGate = gate())

    assertEquals(emptyList(), skipped.filter { id -> id == "fetch" || id == "parse" }, "the loop runner skips members, with the loop, when an input of the loop is broken")
    assertEquals(emptyList(), skipped.filter { id -> id == "write" }, "write waits for the loop's verdict")
  }

  @Test
  fun `loop-free graphs give the old results with the default gate`() {
    val diamond = WorkflowFixtures.loadWorkflow("/test-workflows/diamond-4node.cwf.json")
    val outputs = mapOf("A" to mapOf("out-1" to portData(status = PortDataStatus.SUCCESS)))
    val gateOfDiamond = LoopReadinessGate(topology = LoopTopology.of(workflow = diamond))

    val readyWithoutGate = NodeReadiness.getReadyNodes(continuumWorkflow = diamond, nodeOutputMap = outputs, nodeErrorsMap = emptyMap())
    val readyWithGate = NodeReadiness.getReadyNodes(continuumWorkflow = diamond, nodeOutputMap = outputs, nodeErrorsMap = emptyMap(), loopGate = gateOfDiamond)

    assertEquals(listOf("B", "C"), readyWithoutGate.map { node -> node.id })
    assertEquals(readyWithoutGate, readyWithGate)
  }
}
