package org.projectcontinuum.core.orchestration.spike

import dev.cel.bundle.Cel
import dev.cel.bundle.CelFactory
import dev.cel.common.CelOptions
import dev.cel.common.types.MapType
import dev.cel.common.types.SimpleType
import io.temporal.api.enums.v1.EventType
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.testing.WorkflowReplayer
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * cel-java used inside Temporal workflow code: evaluation and replay regression test.
 *
 * A test-only workflow compiles `vars["n"] < 3` once and evaluates it before each `Workflow.sleep` step with
 * n = 0, 1, 2, 3, so it completes with 3 iterations. The test then replays the recorded history with
 * [WorkflowReplayer]: no non-determinism error and no workflow task failure (a `PotentialDeadlockException` fails the
 * workflow task). The engine is warmed up outside workflow code first, as the orchestrator will do at bean start;
 * the first-use time is printed so it can be recorded.
 *
 * Stays as a regression test: it fails when a cel-java or Temporal upgrade breaks in-workflow evaluation.
 */
class CelInWorkflowSpikeTest {

  @WorkflowInterface
  interface CelSpikeWorkflow {
    @WorkflowMethod
    fun countIterations(): Int
  }

  /** Deterministic: compiles and evaluates a pure CEL expression; no clock, random or I/O. */
  class CelSpikeWorkflowImpl : CelSpikeWorkflow {
    override fun countIterations(): Int {
      val celEngine = buildLoopConditionEngine()
      val program = celEngine.createProgram(celEngine.compile(SPIKE_CONDITION).ast)
      var iterationCount = 0
      while (true) {
        val variables = mapOf("n" to iterationCount.toLong())
        val conditionHolds = program.eval(mapOf("vars" to variables)) as Boolean
        if (!conditionHolds) {
          break
        }
        Workflow.sleep(Duration.ofSeconds(1))
        iterationCount += 1
      }
      return iterationCount
    }
  }

  @Test
  fun `cel condition evaluated inside workflow code completes and replays deterministically`() {
    val firstUseStartNanos = System.nanoTime()
    val warmUpEngine = buildLoopConditionEngine()
    val warmUpProgram = warmUpEngine.createProgram(warmUpEngine.compile(SPIKE_CONDITION).ast)
    val warmUpResult = warmUpProgram.eval(mapOf("vars" to mapOf("n" to 0L)))
    val firstUseMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - firstUseStartNanos)
    println("CEL_SPIKE engine first-use (build + compile + eval, outside workflow code): $firstUseMillis ms")
    assertEquals(true, warmUpResult)

    val testEnvironment = TestWorkflowEnvironment.newInstance()
    testEnvironment.use { environment ->
      environment.newWorker(SPIKE_TASK_QUEUE).registerWorkflowImplementationTypes(CelSpikeWorkflowImpl::class.java)
      environment.start()

      val workflowId = "cel-spike-${UUID.randomUUID()}"
      val workflowStub = environment.workflowClient.newWorkflowStub(
        CelSpikeWorkflow::class.java,
        WorkflowOptions.newBuilder()
          .setWorkflowId(workflowId)
          .setTaskQueue(SPIKE_TASK_QUEUE)
          .build()
      )
      val resultFuture = WorkflowClient.execute(workflowStub::countIterations)
      val iterationCount = resultFuture.get(30, TimeUnit.SECONDS)
      assertEquals(3, iterationCount)

      val history = environment.workflowClient.fetchHistory(workflowId)
      val failedWorkflowTasks = history.events.filter { event ->
        event.eventType == EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED
      }
      assertTrue(failedWorkflowTasks.isEmpty(), "no workflow task failed (deadlock detector, exceptions): $failedWorkflowTasks")
      println("CEL_SPIKE history events for 3 iterations: ${history.events.size}")

      WorkflowReplayer.replayWorkflowExecution(history, CelSpikeWorkflowImpl::class.java)
    }
  }

  @Test
  fun `heterogeneous numeric comparison option compares an int variable with a double literal`() {
    val celEngine = buildLoopConditionEngine()
    val program = celEngine.createProgram(celEngine.compile("vars[\"n\"] < 2.5").ast)

    assertEquals(true, program.eval(mapOf("vars" to mapOf("n" to 2L))))
    assertEquals(false, program.eval(mapOf("vars" to mapOf("n" to 3L))))
  }

  companion object {
    private const val SPIKE_TASK_QUEUE = "CEL_SPIKE_TASK_QUEUE"
    private const val SPIKE_CONDITION = "vars[\"n\"] < 3"

    /** One declared variable `vars: map(string, dyn)`; int and double compare by value. */
    fun buildLoopConditionEngine(): Cel {
      val celOptions = CelOptions.current()
        .enableHeterogeneousNumericComparisons(true)
        .build()
      return CelFactory.standardCelBuilder()
        .setOptions(celOptions)
        .addVar("vars", MapType.create(SimpleType.STRING, SimpleType.DYN))
        .build()
    }
  }
}
