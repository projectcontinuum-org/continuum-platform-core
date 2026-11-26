package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopWorkflowBuilder
import org.projectcontinuum.core.orchestration.workflow.fixtures.WorkflowFixtures
import org.projectcontinuum.core.orchestration.workflow.loop.LoopReadinessGate
import org.projectcontinuum.core.orchestration.workflow.loop.LoopTopology
import org.projectcontinuum.core.orchestration.workflow.loop.NodeDispatchPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Pure plumbing pieces of loop runs: loop nodes in readiness, dispatch payload and progress. */
class LoopPlumbingTest {

  private fun portData(status: PortDataStatus) =
    PortData(status = status, contentType = "text/plain", tableSpec = emptyList(), data = "x")

  private val delivered = mapOf("out" to portData(status = PortDataStatus.SUCCESS))
  private val failed = mapOf("\$error" to portData(status = PortDataStatus.FAILED))

  /** seed -> [fetch -> parse] -> write, plus an empty loop E at the top level. */
  private val model = LoopWorkflowBuilder()
    .source(id = "seed")
    .loop(id = "L")
    .step(id = "fetch", parent = "L")
    .step(id = "parse", parent = "L")
    .step(id = "write")
    .loop(id = "E")
    .edge(from = "seed", to = "fetch")
    .edge(from = "fetch", to = "parse")
    .edge(from = "parse", to = "write")
    .build()

  @Test
  fun `a loop node is never ready, with or without a gate`() {
    val everythingDone = mapOf("seed" to delivered, "fetch" to delivered, "parse" to delivered, "write" to delivered)
    val activeGate = LoopReadinessGate(topology = LoopTopology.of(workflow = model), activeLoopIds = setOf("L", "E"))

    val readyWithoutGate = NodeReadiness.getReadyNodes(continuumWorkflow = model, nodeOutputMap = emptyMap(), nodeErrorsMap = emptyMap())
    val readyWithGate = NodeReadiness.getReadyNodes(
      continuumWorkflow = model,
      nodeOutputMap = emptyMap(),
      nodeErrorsMap = emptyMap(),
      loopGate = activeGate
    )
    val readyAtTheEnd = NodeReadiness.getReadyNodes(continuumWorkflow = model, nodeOutputMap = everythingDone, nodeErrorsMap = emptyMap())

    assertEquals(listOf("seed"), readyWithoutGate.map { node -> node.id })
    assertEquals(listOf("seed"), readyWithGate.map { node -> node.id })
    assertEquals(emptyList(), readyAtTheEnd.map { node -> node.id })
  }

  @Test
  fun `a loop node is never skipped, even when everything around it failed`() {
    val skipped = NodeReadiness.getNodesToSkip(
      continuumWorkflow = model,
      nodeOutputMap = emptyMap(),
      nodeErrorsMap = mapOf("seed" to failed)
    )

    assertEquals(listOf("fetch", "parse", "write"), skipped.map { node -> node.id })
  }

  @Test
  fun `dispatch strips the loop fields and leaves a loop-free node untouched`() {
    val member = model.nodes.single { node -> node.id == "fetch" }.let { node -> node.copy(data = node.data.copy(collapsed = true)) }
    val topLevel = WorkflowFixtures.loadWorkflow(classpathResource = "/test-workflows/linear-2node.cwf.json").nodes.first()

    val dispatchedMember = NodeDispatchPayload.of(node = member)
    val dispatchedTopLevel = NodeDispatchPayload.of(node = topLevel)

    assertNull(dispatchedMember.parentNode)
    assertNull(dispatchedMember.data.collapsed)
    assertEquals(member.copy(parentNode = null, data = member.data.copy(collapsed = null)), dispatchedMember)
    assertEquals("L", member.parentNode, "the run's own model keeps its loop fields")
    assertSame(topLevel, dispatchedTopLevel)
  }

  @Test
  fun `progress counts only non-loop nodes`() {
    val halfDone = WorkflowProgress.percentage(
      nodes = model.nodes,
      nodeToOutputsMap = mapOf("seed" to delivered, "fetch" to delivered),
      nodeErrorsMap = emptyMap()
    )
    val allDone = WorkflowProgress.percentage(
      nodes = model.nodes,
      nodeToOutputsMap = mapOf("seed" to delivered, "fetch" to delivered, "parse" to delivered),
      nodeErrorsMap = mapOf("write" to failed)
    )
    val onlyLoops = WorkflowProgress.percentage(
      nodes = model.nodes.filter { node -> node.id == "L" || node.id == "E" },
      nodeToOutputsMap = emptyMap(),
      nodeErrorsMap = emptyMap()
    )

    assertEquals(50, halfDone)
    assertEquals(100, allDone)
    assertEquals(100, onlyLoops)
  }

  @Test
  fun `progress counts skipped nodes as done, once`() {
    val skippedWrite = model.nodes.map { node ->
      if (node.id == "write") node.copy(data = node.data.copy(status = ContinuumWorkflowModel.NodeStatus.SKIPPED)) else node
    }

    val percentage = WorkflowProgress.percentage(
      nodes = skippedWrite,
      nodeToOutputsMap = mapOf("seed" to delivered),
      nodeErrorsMap = emptyMap()
    )

    assertEquals(50, percentage)
  }

  @Test
  fun `published progress never goes down`() {
    val progress = MonotonicProgress()

    val published = listOf(0, 25, 50, 25, 75, 0, 100).map { raw -> progress.next(rawPercentage = raw) }

    assertEquals(listOf(0, 25, 50, 50, 75, 75, 100), published)
  }
}
