package org.projectcontinuum.core.orchestration.workflow

import io.temporal.api.enums.v1.EventType
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel.NodeStatus
import org.projectcontinuum.core.commons.utils.FlowVariables
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionOutcome
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopFixtures
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopRunHarness
import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopWorkflowBuilder
import org.projectcontinuum.core.orchestration.workflow.fixtures.ScriptedLoopConditionEvaluator
import org.projectcontinuum.core.orchestration.workflow.loop.LoopHistoryGuard
import org.projectcontinuum.core.orchestration.workflow.loop.RunHistory
import org.projectcontinuum.core.orchestration.workflow.loop.WorkflowLoopsFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Loops execute: members run once per iteration, the condition is checked on the end-of-iteration variables, limits,
 * failures, non-boolean results and nested loops behave as the Loop documentation says. The condition outcomes are
 * scripted, so these tests do not depend on the CEL engine.
 */
class ContinuumWorkflowLoopTest {

  private val members = listOf("fetch", "parse", "append")

  private fun hasMoreFor(vararg values: Boolean): List<Map<String, Any>> = values.map { value -> mapOf("hasMore" to value) }

  @Test
  fun `the paging loop runs three iterations, then the node after it runs once with the last iteration`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator.whileTrue(name = "hasMore")).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = hasMoreFor(true, true, false)

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow())

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      members.forEach { member -> assertEquals(3, harness.fakeNodeActivity.invocationCount.getValue(member).get(), member) }
      assertEquals(1, harness.fakeNodeActivity.invocationCount.getValue("write").get())
      assertEquals(1, harness.fakeNodeActivity.invocationCount.getValue("seed").get())
      val loop = harness.lastPublished(nodeId = "L")
      assertEquals(NodeStatus.SUCCESS, loop.data.status)
      assertEquals("Finished after 3 iterations", loop.data.nodeProgress?.message)
      val writeVariables = FlowVariables.extract(harness.fakeNodeActivity.receivedInputs.getValue("write"))
      assertEquals(false, writeVariables["hasMore"], "write inherits the iteration-3 variables")
      assertEquals("FINISHED", harness.events.last().data.status)
    }
  }

  @Test
  fun `check before with a false condition runs zero iterations and skips the members and their consumers`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator.whileTrue(name = "hasMore")).use { harness ->
      harness.fakeNodeActivity.flowVariablesToSet["seed"] = mapOf("hasMore" to false)

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("check" to "before")))

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      members.forEach { member -> assertNull(harness.fakeNodeActivity.invocationCount[member], member) }
      (members + "write").forEach { nodeId -> assertEquals(NodeStatus.SKIPPED, harness.lastPublished(nodeId = nodeId).data.status, nodeId) }
      val loop = harness.lastPublished(nodeId = "L")
      assertEquals(NodeStatus.SUCCESS, loop.data.status)
      assertEquals("Condition was false before the first iteration", loop.data.nodeProgress?.message)
      assertEquals("FINISHED", harness.events.last().data.status)
    }
  }

  @Test
  fun `check before with a condition that turns false after two iterations runs the members twice`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator.whileTrue(name = "hasMore")).use { harness ->
      harness.fakeNodeActivity.flowVariablesToSet["seed"] = mapOf("hasMore" to true)
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = hasMoreFor(true, false)

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("check" to "before")))

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      members.forEach { member -> assertEquals(2, harness.fakeNodeActivity.invocationCount.getValue(member).get(), member) }
    }
  }

  @Test
  fun `the limit with fail fails the loop and the run and skips the node after it`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.True })).use { harness ->
      val result = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("maxIterations" to 5)))

      assertTrue(!result.succeeded)
      members.forEach { member -> assertEquals(5, harness.fakeNodeActivity.invocationCount.getValue(member).get(), member) }
      assertNull(harness.fakeNodeActivity.invocationCount["write"])
      assertEquals(NodeStatus.FAILED, harness.lastPublished(nodeId = "L").data.status)
      assertEquals(NodeStatus.SKIPPED, harness.lastPublished(nodeId = "write").data.status)
      val error = harness.lastError(nodeId = "L")
      assertEquals("Loop 'L' reached its limit of 5 iterations and the condition still holds.", error)
      assertEquals("FAILED", harness.events.last().data.status, "the run record of a run whose loop failed is FAILED")
    }
  }

  @Test
  fun `the limit with stop-with-warning ends the loop with a warning and the node after it runs`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.True })).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = (1..5).map { page -> mapOf("page" to page) }

      val result = harness.run(
        workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("maxIterations" to 5, "onLimit" to "stop-with-warning"))
      )

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      val loop = harness.lastPublished(nodeId = "L")
      assertEquals(NodeStatus.WARNING, loop.data.status)
      assertEquals("Stopped at the limit of 5 iterations", loop.data.nodeProgress?.message)
      val writeVariables = FlowVariables.extract(harness.fakeNodeActivity.receivedInputs.getValue("write"))
      assertTrue(FlowVariables.valuesEqual(first = 5, second = writeVariables["page"]), "write sees iteration 5: $writeVariables")
    }
  }

  @Test
  fun `a member that fails in iteration 2 stops the loop and fails the run`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.True })).use { harness ->
      harness.fakeNodeActivity.errorOnInvocation["parse"] = 2

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow())

      assertTrue(!result.succeeded)
      assertEquals(2, harness.fakeNodeActivity.invocationCount.getValue("fetch").get())
      assertNull(harness.fakeNodeActivity.invocationCount["append"]?.get()?.takeIf { count -> count > 1 })
      assertEquals(NodeStatus.FAILED, harness.lastPublished(nodeId = "L").data.status)
      assertEquals(NodeStatus.SKIPPED, harness.lastPublished(nodeId = "write").data.status)
      assertTrue(harness.lastError(nodeId = "L")!!.contains("iteration 2"))
      assertEquals("FAILED", harness.events.last().data.status)
    }
  }

  @Test
  fun `between iterations the members and inner edges are reset, and the consumer waits for the loop`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator.whileTrue(name = "hasMore")).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = hasMoreFor(true, false)

      harness.run(workflow = LoopFixtures.pagingWorkflow())

      val invoked = harness.fakeNodeActivity.invokedNodeIds.toList()
      assertEquals(listOf("seed", "fetch", "parse", "append", "fetch", "parse", "append", "write"), invoked)
      val iterationTwoStart = harness.events.indexOfFirst { event ->
        event.data.workflow.nodes.single { node -> node.id == "L" }.data.nodeProgress?.message == "Iteration 2 of 50"
      }
      assertTrue(iterationTwoStart > 0)
      val atIterationTwo = harness.events[iterationTwoStart].data
      listOf("parse", "append").forEach { member ->
        assertNull(atIterationTwo.workflow.nodes.single { node -> node.id == member }.data.status, "$member reset")
        assertTrue(member !in atIterationTwo.nodeToOutputsMap.keys, "$member outputs cleared")
      }
      val innerEdges = atIterationTwo.workflow.edges.filter { edge -> edge.source in members && edge.target in members }
      assertTrue(innerEdges.all { edge -> edge.data == null && edge.animated != true }, "inner edges reset: $innerEdges")
    }
  }

  @Test
  fun `nested loops run the inner members outer times inner iterations`() {
    val workflow = LoopFixtures.withLoopProperties(
      workflow = LoopFixtures.withLoopProperties(
        workflow = LoopWorkflowBuilder()
          .source(id = "seed")
          .loop(id = "O")
          .step(id = "a", parent = "O")
          .loop(id = "I", parent = "O")
          .step(id = "b", parent = "I")
          .step(id = "after")
          .edge(from = "seed", to = "a")
          .edge(from = "a", to = "b")
          .edge(from = "b", to = "after")
          .build(),
        loopProperties = mapOf("maxIterations" to 2, "onLimit" to "stop-with-warning"),
        loopId = "O"
      ),
      loopProperties = mapOf("maxIterations" to 3, "onLimit" to "stop-with-warning"),
      loopId = "I"
    )
    val evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.True })
    LoopRunHarness(evaluator = evaluator).use { harness ->
      val result = harness.run(workflow = workflow)

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      assertEquals(2, harness.fakeNodeActivity.invocationCount.getValue("a").get())
      assertEquals(6, harness.fakeNodeActivity.invocationCount.getValue("b").get())
      assertEquals(1, harness.fakeNodeActivity.invocationCount.getValue("after").get())
      assertEquals(
        expected = 2,
        actual = evaluator.compiledTexts.size,
        message = "each loop condition is compiled once for the workflow run"
      )
      assertEquals(
        expected = 10,
        actual = sideEffectMarkers(harness = harness, workflowId = result.workflowId),
        message = "each loop start is recorded once"
      )
    }
  }

  @Test
  fun `carried variables reach the next iteration's entry member over the outside value, and the condition reads them`() {
    val evaluator = ScriptedLoopConditionEvaluator(decide = { variables, _ ->
      if (FlowVariables.valuesEqual(first = variables["page"], second = 3)) LoopConditionOutcome.False else LoopConditionOutcome.True
    })
    LoopRunHarness(evaluator = evaluator).use { harness ->
      harness.fakeNodeActivity.flowVariablesToSet["seed"] = mapOf("page" to 1)
      harness.fakeNodeActivity.flowVariablesByInvocation["append"] = listOf(mapOf("page" to 2), mapOf("page" to 3))

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow())

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      val fetchInputs = harness.fakeNodeActivity.receivedInputsByInvocation.getValue("fetch").map { inputs -> FlowVariables.extract(inputs)["page"] }
      assertTrue(FlowVariables.valuesEqual(first = listOf(1, 2), second = fetchInputs), "fetch sees $fetchInputs")
      val seen = evaluator.seenVariables.map { variables -> variables["page"] }
      assertTrue(FlowVariables.valuesEqual(first = listOf(2, 3), second = seen), "condition read $seen")
      val loopVariables = FlowVariables.extract(harness.lastOutputs(nodeId = "L")!!)
      assertTrue(FlowVariables.valuesEqual(first = 2, second = loopVariables["page"]), "loop \$variable: $loopVariables")
    }
  }

  @Test
  fun `every update carries the history event count and the Temporal limit`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator.whileTrue(name = "hasMore")).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = hasMoreFor(true, false)

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow())

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      val counts = harness.events.map { event -> event.data.historyEventCount }
      assertTrue(counts.all { count -> count != null && count > 0 }, "counts: $counts")
      assertTrue(counts.last()!! > counts.first()!!, "the count grows during the run: $counts")
      assertTrue(harness.events.all { event -> event.data.historyEventLimit == 51_200L })
    }
  }

  @Test
  fun `members and the condition get the read-only iteration variable, which a member cannot override or carry`() {
    val evaluator = ScriptedLoopConditionEvaluator(decide = { variables, _ ->
      if (FlowVariables.valuesEqual(first = variables[FlowVariables.ITERATION], second = 3)) LoopConditionOutcome.False else LoopConditionOutcome.True
    })
    LoopRunHarness(evaluator = evaluator).use { harness ->
      harness.fakeNodeActivity.flowVariablesToSet["append"] = mapOf(FlowVariables.ITERATION to 99)

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow())

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      val fetchIterations = harness.fakeNodeActivity.receivedInputsByInvocation.getValue("fetch")
        .map { inputs -> FlowVariables.extract(inputs)[FlowVariables.ITERATION] }
      assertTrue(FlowVariables.valuesEqual(first = listOf(1, 2, 3), second = fetchIterations), "fetch saw $fetchIterations")
      val conditionIterations = evaluator.seenVariables.map { variables -> variables[FlowVariables.ITERATION] }
      assertTrue(FlowVariables.valuesEqual(first = listOf(1, 2, 3), second = conditionIterations), "condition read $conditionIterations")
      val writeVariables = FlowVariables.extract(harness.fakeNodeActivity.receivedInputs.getValue("write"))
      assertNull(writeVariables[FlowVariables.ITERATION], "a node after the loop gets no iteration")
    }
  }

  @Test
  fun `the node after a finished loop gets the last iteration's carried variables, also those set only in iteration 1`() {
    val workflow = LoopWorkflowBuilder()
      .source(id = "seed")
      .loop(id = "L")
      .step(id = "fetch", parent = "L")
      .step(id = "write")
      .edge(from = "seed", to = "fetch")
      .edge(from = "fetch", to = "write")
      .build()
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator.whileTrue(name = "hasMore")).use { harness ->
      harness.fakeNodeActivity.flowVariablesToSet["seed"] = mapOf("page" to 1)
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = listOf(
        mapOf("token" to "t1", "page" to 2, "hasMore" to true),
        mapOf("hasMore" to true),
        mapOf("hasMore" to false)
      )

      val result = harness.run(workflow = workflow)

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      assertEquals(3, harness.fakeNodeActivity.invocationCount.getValue("fetch").get())
      val writeVariables = FlowVariables.extract(harness.fakeNodeActivity.receivedInputs.getValue("write"))
      val expectedWriteVariables = mapOf("page" to 2, "token" to "t1", "hasMore" to false)
      assertTrue(FlowVariables.valuesEqual(first = expectedWriteVariables, second = writeVariables), "write received $writeVariables")

      val displayedCarried = FlowVariables.extract(harness.lastOutputs(nodeId = "L")!!)
      val expectedDisplayedCarried = mapOf("token" to "t1", "page" to 2, "hasMore" to true)
      assertTrue(FlowVariables.valuesEqual(first = expectedDisplayedCarried, second = displayedCarried), "loop \$variable: $displayedCarried")
      val fetchInLastIteration = FlowVariables.extract(harness.fakeNodeActivity.receivedInputsByInvocation.getValue("fetch").last()) - FlowVariables.ITERATION
      val seedVariables = mapOf("page" to 1)
      assertTrue(
        FlowVariables.valuesEqual(first = seedVariables + displayedCarried, second = fetchInLastIteration),
        "the displayed carried set is what the last iteration's entry member received over the outside value: $fetchInLastIteration"
      )
    }
  }

  private fun sideEffectMarkers(harness: LoopRunHarness, workflowId: String): Int =
    harness.history(workflowId = workflowId).eventsList.count { event ->
      event.eventType == EventType.EVENT_TYPE_MARKER_RECORDED && event.markerRecordedEventAttributes.markerName == "SideEffect"
    }

  private val alwaysTrueAtTheHighestLimit = mapOf("maxIterations" to 10_000, "onLimit" to "stop-with-warning")

  @Test
  fun `an empty loop runs no iteration and ends with a warning at once, even with the highest limit`() {
    val workflow = LoopFixtures.withLoopProperties(
      workflow = LoopWorkflowBuilder().source(id = "seed").loop(id = "L").build(),
      loopProperties = alwaysTrueAtTheHighestLimit
    )
    val evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.True })
    LoopRunHarness(evaluator = evaluator).use { harness ->
      val result = harness.run(workflow = workflow)

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      val loop = harness.lastPublished(nodeId = "L")
      assertEquals(NodeStatus.WARNING, loop.data.status)
      assertEquals("The loop has no nodes inside it to run; it ran no iteration", loop.data.nodeProgress?.message)
      assertTrue(evaluator.seenVariables.isEmpty(), "the condition is never checked")
      assertEquals(
        expected = 1,
        actual = sideEffectMarkers(harness = harness, workflowId = result.workflowId),
        message = "the start verdict is recorded"
      )
      assertEquals("FINISHED", harness.events.last().data.status)
    }
  }

  @Test
  fun `a loop holding only an empty loop, both at the highest limit, ends at once without checking a condition`() {
    val workflow = LoopFixtures.withLoopProperties(
      workflow = LoopWorkflowBuilder().source(id = "seed").loop(id = "O").loop(id = "I", parent = "O").build(),
      loopProperties = alwaysTrueAtTheHighestLimit
    )
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.True })).use { harness ->
      val result = harness.run(workflow = workflow)

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      assertEquals(NodeStatus.WARNING, harness.lastPublished(nodeId = "O").data.status)
      assertEquals(NodeStatus.SKIPPED, harness.lastPublished(nodeId = "I").data.status)
      assertEquals(
        expected = 1,
        actual = sideEffectMarkers(harness = harness, workflowId = result.workflowId),
        message = "the outer start verdict is recorded once"
      )
    }
  }

  @Test
  fun `an empty loop nested in a loop with a member finishes in every outer iteration without spinning`() {
    val workflow = LoopFixtures.withLoopProperties(
      workflow = LoopFixtures.withLoopProperties(
        workflow = LoopWorkflowBuilder()
          .source(id = "seed")
          .loop(id = "O")
          .step(id = "a", parent = "O")
          .loop(id = "I", parent = "O")
          .edge(from = "seed", to = "a")
          .build(),
        loopProperties = mapOf("maxIterations" to 6, "onLimit" to "stop-with-warning"),
        loopId = "O"
      ),
      loopProperties = alwaysTrueAtTheHighestLimit,
      loopId = "I"
    )
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.True })).use { harness ->
      val result = harness.run(workflow = workflow)

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      assertEquals(6, harness.fakeNodeActivity.invocationCount.getValue("a").get())
      assertEquals(NodeStatus.WARNING, harness.lastPublished(nodeId = "O").data.status)
      assertEquals(NodeStatus.WARNING, harness.lastPublished(nodeId = "I").data.status)
      assertEquals(
        expected = 8,
        actual = sideEffectMarkers(harness = harness, workflowId = result.workflowId),
        message = "the two start verdicts are recorded once, with one marker per outer condition check"
      )
    }
  }

  @Test
  fun `a loop whose members are all skipped is skipped after its first iteration and the node after it too`() {
    val workflow = LoopFixtures.withLoopProperties(
      workflow = LoopWorkflowBuilder()
        .loop(id = "L")
        .step(id = "unfed", parent = "L")
        .step(id = "after")
        .edge(from = "unfed", to = "after")
        .build(),
      loopProperties = alwaysTrueAtTheHighestLimit
    )
    val evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.True })
    LoopRunHarness(evaluator = evaluator).use { harness ->
      val result = harness.run(workflow = workflow)

      assertTrue(result.succeeded, "a skipped loop is not a failure: ${result.failure}")
      val loop = harness.lastPublished(nodeId = "L")
      assertEquals(NodeStatus.SKIPPED, loop.data.status)
      assertEquals("No node inside the loop could run in iteration 1", loop.data.nodeProgress?.message)
      assertEquals(NodeStatus.SKIPPED, harness.lastPublished(nodeId = "unfed").data.status)
      assertEquals(NodeStatus.SKIPPED, harness.lastPublished(nodeId = "after").data.status)
      assertNull(harness.fakeNodeActivity.invocationCount["unfed"])
      assertTrue(evaluator.seenVariables.isEmpty(), "the condition is never checked")
      assertEquals(
        expected = 1,
        actual = sideEffectMarkers(harness = harness, workflowId = result.workflowId),
        message = "the start verdict is recorded"
      )
    }
  }

  @Test
  fun `a non-boolean result fails the loop under fail and names the type, a preview and the iteration`() {
    val evaluator = ScriptedLoopConditionEvaluator(
      scriptedOutcomes = listOf(LoopConditionOutcome.True, LoopConditionOutcome.NonBoolean(typeName = "string", preview = "\"yes\""))
    )
    LoopRunHarness(evaluator = evaluator).use { harness ->
      val result = harness.run(workflow = LoopFixtures.pagingWorkflow())

      assertTrue(!result.succeeded)
      assertEquals(NodeStatus.FAILED, harness.lastPublished(nodeId = "L").data.status)
      assertEquals(
        "Loop 'L': the condition returned a string (\"yes\") in iteration 2, not true or false.",
        harness.lastError(nodeId = "L")
      )
    }
  }

  @Test
  fun `a non-boolean result under treat-as-false ends the loop with a warning and the node after it runs`() {
    val evaluator = ScriptedLoopConditionEvaluator(
      scriptedOutcomes = listOf(LoopConditionOutcome.True, LoopConditionOutcome.NonBoolean(typeName = "string", preview = "\"yes\""))
    )
    LoopRunHarness(evaluator = evaluator).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = listOf(mapOf("page" to 1), mapOf("page" to 2))

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("onNonBooleanResult" to "treat-as-false")))

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      val loop = harness.lastPublished(nodeId = "L")
      assertEquals(NodeStatus.WARNING, loop.data.status)
      assertEquals("Condition returned string in iteration 2; treated as false.", loop.data.nodeProgress?.message)
      val writeVariables = FlowVariables.extract(harness.fakeNodeActivity.receivedInputs.getValue("write"))
      assertTrue(FlowVariables.valuesEqual(first = 2, second = writeVariables["page"]))
    }
  }

  @Test
  fun `a non-boolean result on the first check before skips the members and warns`() {
    val evaluator = ScriptedLoopConditionEvaluator(scriptedOutcomes = listOf(LoopConditionOutcome.NonBoolean(typeName = "int", preview = "3")))
    LoopRunHarness(evaluator = evaluator).use { harness ->
      val result = harness.run(
        workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("check" to "before", "onNonBooleanResult" to "treat-as-false"))
      )

      assertTrue(result.succeeded, "run failed: ${result.failure}")
      members.forEach { member -> assertEquals(NodeStatus.SKIPPED, harness.lastPublished(nodeId = member).data.status, member) }
      assertEquals(NodeStatus.WARNING, harness.lastPublished(nodeId = "L").data.status)
    }
  }

  @Test
  fun `a missing variable fails the run with the loop, the variable, the iteration and the column`() {
    val evaluator = ScriptedLoopConditionEvaluator(
      scriptedOutcomes = listOf(
        LoopConditionOutcome.True,
        LoopConditionOutcome.True,
        LoopConditionOutcome.Failed(
          code = LoopConditionIssueCode.MISSING_VARIABLE,
          message = "reads a flow variable that is not set: 'hasMore' (column 1)",
          variableName = "hasMore",
          column = 1
        )
      )
    )
    LoopRunHarness(evaluator = evaluator).use { harness ->
      val result = harness.run(workflow = LoopFixtures.pagingWorkflow())

      assertTrue(!result.succeeded)
      assertEquals(
        "Loop 'L': flow variable 'hasMore' is not set when the condition is checked in iteration 3 (column 1).",
        harness.lastError(nodeId = "L")
      )
    }
  }

  @Test
  fun `progress never decreases, ends at 100 and the loop shows its iteration`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator.whileTrue(name = "hasMore")).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = hasMoreFor(true, true, false)

      harness.run(workflow = LoopFixtures.pagingWorkflow())

      val progress = harness.events.map { event -> event.data.progressPercentage }
      progress.zipWithNext().forEach { (previous, next) -> assertTrue(next >= previous, "progress went down: $progress") }
      assertEquals(100, progress.last())
      val iterationMessages = harness.events
        .mapNotNull { event -> event.data.workflow.nodes.single { node -> node.id == "L" }.data.nodeProgress?.message }
        .distinct()
      assertTrue(iterationMessages.containsAll(listOf("Iteration 1 of 50", "Iteration 2 of 50", "Iteration 3 of 50")), "$iterationMessages")
    }
  }

  @Test
  fun `every condition check is one side-effect marker in the history`() {
    LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator.whileTrue(name = "hasMore")).use { harness ->
      harness.fakeNodeActivity.flowVariablesByInvocation["fetch"] = hasMoreFor(true, true, false)

      val result = harness.run(workflow = LoopFixtures.pagingWorkflow())

      val markers = harness.history(workflowId = result.workflowId).eventsList.count { event ->
        event.eventType == EventType.EVENT_TYPE_MARKER_RECORDED && event.markerRecordedEventAttributes.markerName == "SideEffect"
      }
      assertEquals(
        expected = 4,
        actual = markers,
        message = "one start verdict plus one marker per condition check"
      )
    }
  }

  @Test
  fun `the history guard fails the loop even with stop-with-warning`() {
    val previousFactory = WorkflowLoopsFactory.current
    WorkflowLoopsFactory.current = WorkflowLoopsFactory(historyGuard = {
      LoopHistoryGuard(history = RunHistory.TEMPORAL, maxHistoryEvents = 30)
    })
    try {
      LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator(decide = { _, _ -> LoopConditionOutcome.True })).use { harness ->
        val result = harness.run(
          workflow = LoopFixtures.pagingWorkflow(loopProperties = mapOf("onLimit" to "stop-with-warning", "maxIterations" to 50))
        )

        assertTrue(!result.succeeded)
        val error = assertNotNull(harness.lastError(nodeId = "L"))
        assertTrue(error.startsWith("Loop 'L' stopped before iteration"), error)
        assertTrue(error.contains("would reach Temporal's limit"), error)
      }
    } finally {
      WorkflowLoopsFactory.current = previousFactory
    }
  }

  @Test
  fun `an invalid condition or setting started directly through Temporal fails at loop start without a task retry loop`() {
    listOf(
      mapOf("condition" to "now() > 1") to "does not type-check",
      mapOf("onNonBooleanResult" to "maybe") to "onNonBooleanResult 'maybe'"
    ).forEach { (properties, expectedReason) ->
      LoopRunHarness(evaluator = ScriptedLoopConditionEvaluator()).use { harness ->
        val result = harness.run(workflow = LoopFixtures.pagingWorkflow(loopProperties = properties))

        assertTrue(!result.succeeded)
        val error = assertNotNull(harness.lastError(nodeId = "L"))
        assertTrue(error.startsWith("Loop 'L' cannot start:") && error.contains(expectedReason), error)
        members.forEach { member -> assertNull(harness.fakeNodeActivity.invocationCount[member], member) }
        val failedTasks = harness.history(workflowId = result.workflowId).eventsList.count { event ->
          event.eventType == EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED
        }
        assertEquals(0, failedTasks)
      }
    }
  }
}
