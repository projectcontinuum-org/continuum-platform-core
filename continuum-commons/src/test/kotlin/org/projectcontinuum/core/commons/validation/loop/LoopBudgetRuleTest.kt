package org.projectcontinuum.core.commons.validation.loop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.validation.GraphNode

/** The run-history warning: over the budget one warning on maxIterations, at or under it none. */
class LoopBudgetRuleTest {

  private fun scopesWithMembers(memberCount: Int): LoopScopes {
    val nodes = listOf(GraphNode(key = "L", nodeId = LoopNode.NODE_MODEL)) +
      (1..memberCount).map { index -> GraphNode(key = "m$index", nodeId = "org.test.Step", parent = "L") }
    return LoopScopes.of(graphNodes = nodes, acceptedKeys = nodes.map { node -> node.key!! }.toSet())
  }

  @Test
  fun `the budget counts members and the condition check per iteration`() {
    assertEquals(31L * 1000, LoopBudget.eventsFor(maxIterations = 1000, memberCount = 3))
    assertEquals(1290L, LoopBudget.ceilingFor(memberCount = 3))
  }

  @Test
  fun `at the budget there is no warning`() {
    val atBudget = LoopBudget.ceilingFor(memberCount = 3).toInt()

    assertTrue(LoopBudgetRule.warnings(scopes = scopesWithMembers(memberCount = 3), maxIterationsByLoopKey = mapOf("L" to atBudget)).isEmpty())
  }

  @Test
  fun `over the budget there is one warning on maxIterations`() {
    val warnings = LoopBudgetRule.warnings(scopes = scopesWithMembers(memberCount = 3), maxIterationsByLoopKey = mapOf("L" to 10_000))

    val warning = warnings.single()
    assertEquals("L", warning.node)
    assertEquals("maxIterations", warning.property)
    assertEquals(
      "Loop 'L': maxIterations 10000 x 3 nodes inside may exceed the run history limit (about 1290 iterations with 3 " +
        "nodes inside). The run stops with an error when the history is nearly full.",
      warning.message
    )
  }

  @Test
  fun `members of nested loops count and an empty loop gets no budget warning`() {
    val nodes = listOf(
      GraphNode(key = "O", nodeId = LoopNode.NODE_MODEL),
      GraphNode(key = "a", nodeId = "org.test.Step", parent = "O"),
      GraphNode(key = "I", nodeId = LoopNode.NODE_MODEL, parent = "O"),
      GraphNode(key = "b", nodeId = "org.test.Step", parent = "I"),
      GraphNode(key = "E", nodeId = LoopNode.NODE_MODEL)
    )
    val scopes = LoopScopes.of(graphNodes = nodes, acceptedKeys = nodes.map { node -> node.key!! }.toSet())

    val warnings = LoopBudgetRule.warnings(scopes = scopes, maxIterationsByLoopKey = mapOf("O" to 2000, "E" to 10_000))

    assertEquals(listOf("O"), warnings.map { warning -> warning.node })
    assertTrue(warnings.single().message.contains("x 2 nodes inside"))
  }
}
