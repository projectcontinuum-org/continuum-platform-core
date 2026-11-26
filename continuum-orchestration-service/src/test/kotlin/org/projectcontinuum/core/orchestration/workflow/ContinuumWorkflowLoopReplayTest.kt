package org.projectcontinuum.core.orchestration.workflow

import io.temporal.api.enums.v1.EventType
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import org.projectcontinuum.core.loopcondition.CelLoopConditionEvaluator
import org.projectcontinuum.core.orchestration.config.LoopConditionEngineConfig
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopFixtures
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopRunHarness
import org.projectcontinuum.core.orchestration.workflow.fixtures.ScriptedLoopConditionEvaluator
import org.projectcontinuum.core.orchestration.workflow.loop.LoopConditionEvaluators
import org.projectcontinuum.core.orchestration.workflow.loop.TimeBoxedConditionEvaluationRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Loops with the real cel-java evaluator: a full-CEL condition drives the paging loop, the recorded history replays
 * without non-determinism, and a replay uses the recorded condition outcomes even when the installed evaluator would
 * now decide differently. The replay uses the harness's environment, whose data converter knows the Kotlin model
 * classes, as the orchestrator's does.
 */
class ContinuumWorkflowLoopReplayTest {

  private val condition = "vars[\"page\"] < 3 && vars[\"hasMore\"]"

  private fun runPagingLoop(harness: LoopRunHarness): LoopRunHarness.RunResult {
    harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = listOf(
      mapOf("page" to 1, "hasMore" to true),
      mapOf("page" to 2, "hasMore" to true),
      mapOf("page" to 3, "hasMore" to true)
    )
    return harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to condition)))
  }

  @Test
  fun `a full CEL condition runs three iterations and the history replays`() {
    LoopRunHarness(evaluator = CelLoopConditionEvaluator()).use { harness ->
      val result = runPagingLoop(harness = harness)

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      assertEquals(3, harness.fakeNodeActivity.invocationCount.getValue("fetch").get())
      assertEquals(NodeStatus.SUCCESS, harness.lastPublished(nodeId = "L").data.status)
      val history = harness.executionHistory(workflowId = result.workflowId)
      val sideEffects = history.events.count { event -> event.eventType == EventType.EVENT_TYPE_MARKER_RECORDED }
      assertEquals(3, sideEffects, "one recorded outcome per condition check")
      assertEquals(0, history.events.count { event -> event.eventType == EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED })

      harness.replay(history = history)
    }
  }

  @Test
  fun `a replay uses the recorded outcomes, not the evaluator installed now`() {
    LoopRunHarness(evaluator = CelLoopConditionEvaluator()).use { harness ->
      val result = runPagingLoop(harness = harness)
      val history = harness.executionHistory(workflowId = result.workflowId)

      LoopConditionEvaluators.install(evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.False }))
      harness.replay(history = history)
    }
  }

  @Test
  fun `the real evaluator fails the run on a missing variable and absorbs one in an or`() {
    LoopRunHarness(evaluator = CelLoopConditionEvaluator()).use { harness ->
      val missing = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to "vars[\"hasMore\"] == true")))

      assertTrue(!missing.succeeded)
      assertEquals(
        "Loop 'L': flow variable 'hasMore' is not set when the condition is checked in iteration 1 (column 5).",
        harness.lastError(nodeId = "L")
      )
    }
    LoopRunHarness(evaluator = CelLoopConditionEvaluator()).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = listOf(mapOf("page" to 5), mapOf("page" to 1))

      val absorbed = harness.run(
        workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to "vars[\"hasMore\"] == true || vars[\"page\"] > 3"))
      )

      assertTrue(!absorbed.succeeded, "iteration 2 has page 1 and no hasMore, so it fails there")
      assertEquals(2, harness.fakeNodeActivity.invocationCount.getValue("fetch").get(), "iteration 1 continued: the or absorbed the missing variable")
    }
  }

  @Test
  fun `the real evaluator under treat-as-false ends the loop with a warning on a string result`() {
    LoopRunHarness(evaluator = CelLoopConditionEvaluator()).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = listOf(mapOf("hasMore" to true), mapOf("hasMore" to "yes"))

      val result = harness.run(
        workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to "vars[\"hasMore\"]", "onNonBooleanResult" to "treat-as-false"))
      )

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      assertEquals(NodeStatus.WARNING, harness.lastPublished(nodeId = "L").data.status)
      assertEquals("Condition returned string in iteration 2; treated as false.", harness.lastPublished(nodeId = "L").data.nodeProgress?.message)
    }
  }

  @Test
  fun `a valid but slow condition fails the loop at the time budget instead of wedging the workflow task`() {
    LoopRunHarness(evaluator = CelLoopConditionEvaluator()).use { harness ->
      harness.fakeNodeActivity.flowVariablesToSet["fetch"] = mapOf("x" to 1)
      val slowCondition = "lists.range(20).all(i, sets.contains(lists.range(10000), lists.range(10000).reverse())) && vars[\"x\"] == 1"

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to slowCondition)))

      assertTrue(!result.succeeded)
      val error = harness.lastError(nodeId = "L").orEmpty()
      assertTrue(error.contains("LIMIT_EXCEEDED") && error.contains("took longer than 200 ms to evaluate"), error)
      val history = harness.executionHistory(workflowId = result.workflowId)
      assertEquals(0, history.events.count { event -> event.eventType == EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED })
      harness.replay(history = history)
    }
  }

  @Test
  fun `one slow function call over two large list variables fails the loop without a workflow task failure, and replays`() {
    val oneElementLists = (0 until 9000).map { index -> listOf(index) }
    TimeBoxedConditionEvaluationRunner().use { runner ->
      LoopRunHarness(
        evaluator = CelLoopConditionEvaluator(),
        runner = runner,
        deadlockDetectionTimeoutMillis = STRICT_DEADLOCK_DETECTION_MILLIS
      ).use { harness ->
        // A first run loads the workflow and loop classes, so the strict detector below measures the condition check only.
        val warmUp = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to "has(vars.page)")))
        assertTrue(warmUp.succeeded, "warm-up run failed: ${warmUp.failure}")
        harness.fakeNodeActivity.invocationCount.clear()
        harness.fakeNodeActivity.flowVariablesToSet["fetch"] = mapOf("a" to oneElementLists)
        harness.fakeNodeActivity.flowVariablesToSet["parse"] = mapOf("b" to oneElementLists.reversed())
        val oneSlowCall = "sets.equivalent(vars[\"a\"], vars[\"b\"])"

        val result = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to oneSlowCall)))

        assertTrue(!result.succeeded)
        assertEquals(1, harness.fakeNodeActivity.invocationCount.getValue("fetch").get(), "the loop fails after its first check")
        assertEquals(NodeStatus.FAILED, harness.lastPublished(nodeId = "L").data.status)
        val error = harness.lastError(nodeId = "L").orEmpty()
        assertTrue(error.contains("LIMIT_EXCEEDED") && error.contains("took longer than 200 ms to evaluate"), error)
        val history = harness.executionHistory(workflowId = result.workflowId)
        assertEquals(0, history.events.count { event -> event.eventType == EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED })
        assertEquals(1, history.events.count { event -> event.eventType == EventType.EVENT_TYPE_MARKER_RECORDED }, "the outcome is recorded once")
        harness.replay(history = history)
      }
    }
  }

  @Test
  fun `the engine config installs and warms up the cel-java evaluator`() {
    val config = LoopConditionEngineConfig()
    try {
      config.installEvaluator()

      assertTrue(LoopConditionEvaluators.current is CelLoopConditionEvaluator)
      assertTrue(LoopConditionEvaluators.runner is TimeBoxedConditionEvaluationRunner)
    } finally {
      config.stopEvaluationThreads()
      LoopConditionEvaluators.reset()
    }
  }

  private companion object {
    /**
     * Half of Temporal's one-second default: a check that blocked the workflow thread for most of a second (as one
     * long call did before the bounded wait) fails here even on a fast idle machine.
     */
    const val STRICT_DEADLOCK_DETECTION_MILLIS = 500L
  }
}
