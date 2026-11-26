package org.projectcontinuum.core.orchestration.workflow

import io.temporal.api.enums.v1.EventType
import io.temporal.common.WorkflowExecutionHistory
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.validation.loop.CompiledLoopCondition
import org.projectcontinuum.core.commons.validation.loop.LoopConditionEvaluator
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import org.projectcontinuum.core.commons.validation.loop.LoopConditionResultType
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopFixtures
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopRunHarness
import org.projectcontinuum.core.orchestration.workflow.loop.ConditionEvaluationRunner
import org.projectcontinuum.core.orchestration.workflow.loop.TimeBoxedConditionEvaluationRunner
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The condition evaluation threads are shared by every run on the orchestrator, whoever started it. A check that
 * cannot get a thread because abandoned slow evaluations hold them all says nothing about its own condition: nothing
 * is recorded, Temporal retries the workflow task, and the loop finishes normally once a thread is free. Only a check
 * that actually ran past the time budget fails its loop. A stopped runner (orchestrator shutdown) is retried the same
 * way.
 */
class ContinuumWorkflowLoopEvaluationPoolTest {

  private val warnings: MutableList<String> = Collections.synchronizedList(mutableListOf())

  @Test
  fun `a loop whose check finds every evaluation thread held by other runs finishes normally once a thread is free`() {
    val stuckCallsMayReturn = CountDownLatch(1)
    val evaluator = StuckOrFalseEvaluator(release = stuckCallsMayReturn)
    val runner = TimeBoxedConditionEvaluationRunner(
      warn = { message ->
        warnings += message
        // The first warnings are the slow runs' own checks; the next one is the fast run's check being turned away.
        // The slow calls then return, as a long cel-java call does after about a second.
        if (warnings.size > SLOW_RUNS) stuckCallsMayReturn.countDown()
      }
    )
    try {
      LoopRunHarness(evaluator = evaluator, runner = runner, workflowTaskTimeout = SHORT_WORKFLOW_TASK_TIMEOUT).use { harness ->
        val slowRuns = (1..SLOW_RUNS).map {
          harness.start(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to StuckOrFalseEvaluator.STUCK)))
        }
        val slowResults = slowRuns.map { run -> run.await() }
        assertTrue(slowResults.none { result -> result.succeeded }, "a condition that is itself too slow still fails its loop")
        assertEquals(SLOW_RUNS, evaluator.stuckCallsStarted.get(), "every evaluation thread is now held by an abandoned call")

        val fast = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to StuckOrFalseEvaluator.FAST)))

        assertTrue(fast.succeeded, "the fast run must not fail because of other runs' slow conditions: ${fast.failure}")
        val loop = harness.events.last { event -> event.jobId == fast.workflowId }.data.workflow.nodes.single { node -> node.id == "L" }
        assertEquals(NodeStatus.SUCCESS, loop.data.status)
        assertEquals("Finished after 1 iterations", loop.data.nodeProgress?.message)
        val history = harness.executionHistory(workflowId = fast.workflowId)
        assertEquals(1, markersIn(history = history), "only the real outcome is recorded")
        assertTrue(workflowTaskRetriesIn(history = history) >= 1, "the turned-away check failed its workflow task, which Temporal retried")
        assertTrue(warnings.any { warning -> warning.contains(fast.workflowId) && warning.contains("nothing is recorded and the workflow task is retried") }, "$warnings")
        harness.replay(history = history)
      }
    } finally {
      stuckCallsMayReturn.countDown()
      runner.close()
    }
  }

  @Test
  fun `a check that comes after the evaluation threads were stopped records nothing, and the retried task finishes the loop`() {
    val stoppedRunner = TimeBoxedConditionEvaluationRunner(warn = { message -> warnings += message }).apply { close() }
    val liveRunner = TimeBoxedConditionEvaluationRunner(warn = { message -> warnings += message })
    val runnerCalls = AtomicInteger(0)
    // The first check meets the threads of an orchestrator that is shutting down; the retry runs on a live one.
    val stoppedThenLive = ConditionEvaluationRunner { check, evaluation ->
      val runnerForThisCall = if (runnerCalls.getAndIncrement() == 0) stoppedRunner else liveRunner
      runnerForThisCall.run(check = check, evaluation = evaluation)
    }
    try {
      LoopRunHarness(
        evaluator = StuckOrFalseEvaluator(release = CountDownLatch(0)),
        runner = stoppedThenLive,
        workflowTaskTimeout = SHORT_WORKFLOW_TASK_TIMEOUT
      ).use { harness ->
        val result = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("condition" to StuckOrFalseEvaluator.FAST)))

        assertTrue(result.succeeded, "run failed: ${result.failure}")
        assertEquals(NodeStatus.SUCCESS, harness.lastPublished(nodeId = "L").data.status)
        val history = harness.executionHistory(workflowId = result.workflowId)
        assertEquals(1, markersIn(history = history), "the check on the stopped threads left no marker")
        assertTrue(workflowTaskRetriesIn(history = history) >= 1, "the workflow task was retried")
        assertEquals(2, runnerCalls.get(), "the retried workflow task checked the condition again")
        harness.replay(history = history)
      }
    } finally {
      liveRunner.close()
    }
  }

  private fun markersIn(history: WorkflowExecutionHistory): Int =
    history.events.count { event -> event.eventType == EventType.EVENT_TYPE_MARKER_RECORDED }

  /**
   * Failed or timed-out workflow tasks. The SDK reports the failure of a task's first attempt; a later attempt is left
   * to time out, and the test server sometimes numbers a fresh task as a later attempt, so both count as a retry.
   */
  private fun workflowTaskRetriesIn(history: WorkflowExecutionHistory): Int =
    history.events.count { event ->
      event.eventType == EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED || event.eventType == EventType.EVENT_TYPE_WORKFLOW_TASK_TIMED_OUT
    }

  /**
   * Two valid conditions with scripted behaviour. [STUCK] behaves like one long cel-java function call: it ignores the
   * interrupt and returns only when [release] opens. [FAST] is false at once, so a loop with it finishes after one
   * iteration.
   */
  private class StuckOrFalseEvaluator(private val release: CountDownLatch) : LoopConditionEvaluator {

    val stuckCallsStarted = AtomicInteger(0)

    override fun compile(text: String): CompiledLoopCondition =
      CompiledLoopCondition(text = text, resultType = LoopConditionResultType.BOOL, readVariables = emptySet(), engineHandle = null)

    override fun evaluate(compiled: CompiledLoopCondition, variables: Map<String, Any?>): LoopConditionOutcome {
      if (compiled.text != STUCK) return LoopConditionOutcome.False
      stuckCallsStarted.incrementAndGet()
      val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
      while (release.count > 0 && System.nanoTime() < deadlineNanos) {
        Thread.onSpinWait()
      }
      return LoopConditionOutcome.True
    }

    override fun warmUp() = Unit

    companion object {
      const val STUCK = "vars[\"stuck\"] == true"
      const val FAST = "vars[\"fast\"] == true"
    }
  }

  private companion object {
    /** As many slow runs as the orchestrator has evaluation threads, so together they hold every thread. */
    const val SLOW_RUNS = TimeBoxedConditionEvaluationRunner.EVALUATION_THREADS

    /** Short, so a retried workflow task that the SDK leaves to time out comes back quickly. */
    val SHORT_WORKFLOW_TASK_TIMEOUT: Duration = Duration.ofSeconds(2)
  }
}
