package org.projectcontinuum.core.orchestration.workflow

import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.validation.loop.LoopBudget
import org.projectcontinuum.core.loopcondition.CelLoopConditionEvaluator
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopFixtures
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopRunHarness
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopWorkflowBuilder
import org.projectcontinuum.core.orchestration.workflow.loop.LoopHistoryGuard
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Calibrates the validator's run-history budget: runs a 1-member and a 3-member loop for 1 and 10 iterations with the
 * real evaluator and measures how many history events one member run and one condition check add. Both must stay
 * within 20 % of [LoopBudget]; when Temporal or the orchestrator changes the cost, this test fails first.
 */
class ContinuumWorkflowLoopHistoryBudgetTest {

  private fun historyEvents(memberCount: Int, iterations: Int): Int {
    val builder = LoopWorkflowBuilder().source(id = "seed").loop(id = "L")
    (1..memberCount).forEach { index -> builder.step(id = "m$index", parent = "L") }
    builder.edge(from = "seed", to = "m1")
    (2..memberCount).forEach { index -> builder.edge(from = "m${index - 1}", to = "m$index") }
    val workflow = LoopFixtures.withLoopProperties(
      workflow = builder.build(),
      loopProperties = mapOf("condition" to "vars[\"n\"] < $iterations", "maxIterations" to 100)
    )
    LoopRunHarness(evaluator = CelLoopConditionEvaluator()).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["m$memberCount"] = (1..iterations).map { counter -> mapOf("n" to counter) }
      val result = harness.run(workflow = workflow)
      assertTrue(result.succeeded, "run failed: ${result.failure}")
      assertEquals(iterations, harness.fakeNodeActivity.invocationCount.getValue("m1").get())
      return harness.history(workflowId = result.workflowId).eventsCount
    }
  }

  private fun assertWithinTwentyPercent(expected: Int, measured: Double, label: String) {
    assertTrue(measured >= expected * 0.8 && measured <= expected * 1.2, "$label: measured $measured, constant $expected")
  }

  @Test
  fun `events per member run and per condition check match the budget constants`() {
    val eventsPerIterationWithOneMember = (historyEvents(memberCount = 1, iterations = 10) - historyEvents(memberCount = 1, iterations = 1)) / 9.0
    val eventsPerIterationWithThreeMembers = (historyEvents(memberCount = 3, iterations = 10) - historyEvents(memberCount = 3, iterations = 1)) / 9.0
    val eventsPerMember = (eventsPerIterationWithThreeMembers - eventsPerIterationWithOneMember) / 2.0
    val eventsPerCheck = eventsPerIterationWithOneMember - eventsPerMember
    println("Loop history budget: $eventsPerMember events per member run, $eventsPerCheck per condition check")

    assertWithinTwentyPercent(expected = LoopBudget.EVENTS_PER_MEMBER_EXECUTION, measured = eventsPerMember, label = "events per member run")
    assertWithinTwentyPercent(expected = LoopBudget.EVENTS_PER_CONDITION_CHECK, measured = eventsPerCheck, label = "events per condition check")
  }

  @Test
  fun `the validator budget and the orchestrator's history guard use the same threshold`() {
    assertEquals(LoopBudget.MAX_HISTORY_EVENTS.toLong(), LoopHistoryGuard.MAX_HISTORY_EVENTS)
  }
}
