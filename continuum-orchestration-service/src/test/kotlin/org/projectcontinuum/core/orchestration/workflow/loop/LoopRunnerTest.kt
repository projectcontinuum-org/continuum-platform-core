package org.projectcontinuum.core.orchestration.workflow.loop

import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.utils.FlowVariables
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import org.projectcontinuum.core.loopcondition.LoopConditionValidators
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopFixtures
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopWorkflowBuilder
import org.projectcontinuum.core.orchestration.workflow.fixtures.ScriptedLoopConditionEvaluator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The loop runner's decisions, driven by hand without Temporal: the test plays the part of the DAG executor. */
class LoopRunnerTest {

  private val outputs = mutableMapOf<String, Map<String, PortData>>()
  private val errors = mutableMapOf<String, Map<String, PortData>>()

  private fun runnerFor(workflow: ContinuumWorkflowModel, evaluator: ScriptedLoopConditionEvaluator): LoopRunner {
    val context = LoopRunContext(
      workflow = workflow,
      topology = LoopTopology.of(workflow = workflow),
      nodeToOutputsMap = outputs,
      nodeErrorsMap = errors,
      setNodeStatus = { node, status -> node.data.status = status }
    )
    return LoopRunner(
      context = context,
      evaluator = { evaluator },
      startCheck = LoopStartCheck(validator = LoopConditionValidators.default),
      conditionCheck = LoopConditionCheck(recorder = ConditionOutcomeRecorder.DIRECT),
      historyGuard = LoopHistoryGuard(history = { 0L to 0L })
    )
  }

  private fun complete(workflow: ContinuumWorkflowModel, nodeId: String, variables: Map<String, Any> = emptyMap()) {
    val port = PortData(status = PortDataStatus.SUCCESS, contentType = "text/plain", tableSpec = emptyList(), data = nodeId)
    val variablePort = if (variables.isEmpty()) emptyMap() else mapOf("\$variable" to FlowVariables.toPortData(variables))
    outputs[nodeId] = mapOf("out" to port) + variablePort
    workflow.nodes.single { node -> node.id == nodeId }.data.status = NodeStatus.SUCCESS
  }

  private fun loopFreeOf(workflow: ContinuumWorkflowModel, id: String) = workflow.nodes.single { node -> node.id == id }

  @Test
  fun `a loop waits for its outside input, then starts iteration 1 with check after`() {
    val workflow = LoopFixtures.withLoopProperties(
      workflow = LoopWorkflowBuilder().source(id = "seed").loop(id = "L").step(id = "m", parent = "L").edge(from = "seed", to = "m").build(),
      loopProperties = mapOf("maxIterations" to 3)
    )
    val runner = runnerFor(workflow = workflow, evaluator = ScriptedLoopConditionEvaluator())

    assertFalse(runner.advance(), "seed has not finished")
    complete(workflow = workflow, nodeId = "seed")
    assertTrue(runner.advance())

    assertEquals(setOf("L"), runner.gate().activeLoopIds)
    assertEquals(NodeStatus.BUSY, loopFreeOf(workflow = workflow, id = "L").data.status)
    assertEquals("Iteration 1 of 3", loopFreeOf(workflow = workflow, id = "L").data.nodeProgress?.message)
  }

  @Test
  fun `a finished iteration with a true condition resets the member and starts the next one`() {
    val workflow = LoopFixtures.withLoopProperties(
      workflow = LoopWorkflowBuilder().loop(id = "L").source(id = "m", parent = "L").build(),
      loopProperties = mapOf("maxIterations" to 3)
    )
    val runner = runnerFor(workflow = workflow, evaluator = ScriptedLoopConditionEvaluator(scriptedOutcomes = listOf(LoopConditionOutcome.True)))
    runner.advance()

    complete(workflow = workflow, nodeId = "m", variables = mapOf("page" to 2))
    runner.advance()

    assertEquals("Iteration 2 of 3", loopFreeOf(workflow = workflow, id = "L").data.nodeProgress?.message)
    assertTrue("m" !in outputs, "member outputs are cleared")
    assertEquals(null, loopFreeOf(workflow = workflow, id = "m").data.status)
    assertTrue(FlowVariables.valuesEqual(first = mapOf("page" to 2), second = runner.carriedByLoopId().getValue("L")))
  }

  @Test
  fun `a false condition finishes the loop with success and its carried variables as display output`() {
    val workflow = LoopWorkflowBuilder().loop(id = "L").source(id = "m", parent = "L").build()
    val evaluator = ScriptedLoopConditionEvaluator(scriptedOutcomes = listOf(LoopConditionOutcome.True, LoopConditionOutcome.False))
    val runner = runnerFor(workflow = workflow, evaluator = evaluator)
    runner.advance()
    complete(workflow = workflow, nodeId = "m", variables = mapOf("page" to 2))
    runner.advance()
    complete(workflow = workflow, nodeId = "m", variables = mapOf("page" to 3))
    runner.advance()

    val loop = loopFreeOf(workflow = workflow, id = "L")
    assertEquals(NodeStatus.SUCCESS, loop.data.status)
    assertEquals("Finished after 2 iterations", loop.data.nodeProgress?.message)
    assertTrue(FlowVariables.valuesEqual(first = mapOf("page" to 2), second = FlowVariables.extract(outputs.getValue("L"))))
    assertEquals(setOf("L"), runner.gate().finishedLoopIds)
  }

  @Test
  fun `a broken outside input skips the loop and its members`() {
    val workflow = LoopWorkflowBuilder().source(id = "seed").loop(id = "L").step(id = "m", parent = "L").edge(from = "seed", to = "m").build()
    val runner = runnerFor(workflow = workflow, evaluator = ScriptedLoopConditionEvaluator())
    errors["seed"] = mapOf("\$error" to PortData(status = PortDataStatus.FAILED, contentType = "text/plain", tableSpec = emptyList(), data = "boom"))
    loopFreeOf(workflow = workflow, id = "seed").data.status = NodeStatus.FAILED

    runner.advance()

    assertEquals(NodeStatus.SKIPPED, loopFreeOf(workflow = workflow, id = "L").data.status)
    assertEquals(NodeStatus.SKIPPED, loopFreeOf(workflow = workflow, id = "m").data.status)
    assertEquals(setOf("L"), runner.gate().failedOrSkippedLoopIds)
  }

  @Test
  fun `a failed member fails the loop with an error naming the member`() {
    val workflow = LoopWorkflowBuilder().loop(id = "L").source(id = "m", parent = "L").build()
    val runner = runnerFor(workflow = workflow, evaluator = ScriptedLoopConditionEvaluator())
    runner.advance()
    errors["m"] = mapOf("\$error" to PortData(status = PortDataStatus.FAILED, contentType = "text/plain", tableSpec = emptyList(), data = "boom"))
    loopFreeOf(workflow = workflow, id = "m").data.status = NodeStatus.FAILED

    runner.advance()

    assertEquals(NodeStatus.FAILED, loopFreeOf(workflow = workflow, id = "L").data.status)
    assertEquals("Loop 'L' stopped in iteration 1: node 'm' failed.", errors.getValue("L").getValue("\$error").data)
  }
}
