package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.orchestration.workflow.fixtures.LoopWorkflowBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pure loop structure of a run. */
class LoopTopologyTest {

  /** seed -> [fetch -> parse -> append] -> write */
  private val example = LoopWorkflowBuilder()
    .source(id = "seed")
    .loop(id = "L")
    .step(id = "fetch", parent = "L")
    .step(id = "parse", parent = "L")
    .step(id = "append", parent = "L")
    .step(id = "write")
    .edge(from = "seed", to = "fetch")
    .edge(from = "fetch", to = "parse")
    .edge(from = "parse", to = "append")
    .edge(from = "append", to = "write")
    .build()

  /** seed -> [outer: a -> [inner: b -> c] -> d] -> after */
  private val nested = LoopWorkflowBuilder()
    .source(id = "seed")
    .loop(id = "outer")
    .step(id = "a", parent = "outer")
    .loop(id = "inner", parent = "outer")
    .step(id = "b", parent = "inner")
    .step(id = "c", parent = "inner")
    .step(id = "d", parent = "outer")
    .step(id = "after")
    .edge(from = "seed", to = "a")
    .edge(from = "a", to = "b")
    .edge(from = "b", to = "c")
    .edge(from = "c", to = "d")
    .edge(from = "d", to = "after")
    .build()

  @Test
  fun `the example loop has its members, entry member and boundary edges`() {
    val topology = LoopTopology.of(workflow = example)

    assertEquals(setOf("L"), topology.loopIds)
    assertEquals(listOf("fetch", "parse", "append"), topology.directMembers(loopId = "L"))
    assertEquals(listOf("fetch", "parse", "append"), topology.descendants(loopId = "L"))
    assertEquals(listOf("fetch"), topology.entryMembers(loopId = "L"))
    assertEquals(listOf("seed-fetch"), topology.externalIncomingEdges(loopId = "L").map { edge -> edge.id })
    assertEquals(listOf("append-write"), topology.outwardEdges(loopId = "L").map { edge -> edge.id })
    assertEquals(listOf("fetch-parse", "parse-append"), topology.edgesInside(loopId = "L").map { edge -> edge.id })
    assertEquals("L", topology.innermostLoopOf(nodeId = "parse"))
    assertEquals(null, topology.innermostLoopOf(nodeId = "write"))
  }

  @Test
  fun `nested loops give enclosing loops outermost first and descendants at every depth`() {
    val topology = LoopTopology.of(workflow = nested)

    assertEquals(listOf("outer", "inner"), topology.enclosingLoops(nodeId = "b"))
    assertEquals(emptyList(), topology.enclosingLoops(nodeId = "after"))
    assertEquals(listOf("a", "inner", "d"), topology.directMembers(loopId = "outer"))
    assertEquals(listOf("a", "inner", "b", "c", "d"), topology.descendants(loopId = "outer"))
    assertEquals(listOf("a"), topology.entryMembers(loopId = "outer"))
    assertEquals(listOf("b"), topology.entryMembers(loopId = "inner"))
    assertEquals(listOf("c-d"), topology.outwardEdges(loopId = "inner").map { edge -> edge.id })
    assertEquals(listOf("d-after"), topology.outwardEdges(loopId = "outer").map { edge -> edge.id })
    assertTrue(topology.isInside(nodeId = "c", loopId = "outer"))
    assertFalse(topology.isInside(nodeId = "a", loopId = "inner"))
  }

  @Test
  fun `topological order runs along the edges and breaks ties by node order`() {
    val fanOut = LoopWorkflowBuilder()
      .loop(id = "L")
      .step(id = "z", parent = "L")
      .source(id = "root", parent = "L")
      .step(id = "y", parent = "L")
      .edge(from = "root", to = "z")
      .edge(from = "root", to = "y")
      .build()
    val topology = LoopTopology.of(workflow = fanOut)

    assertEquals(listOf("root", "z", "y"), topology.topologicalOrder(loopId = "L"))
    assertEquals(listOf("a", "b", "c", "d"), LoopTopology.of(workflow = nested).topologicalOrder(loopId = "outer"))
  }

  @Test
  fun `several entry members and none from outside`() {
    val twoRoots = LoopWorkflowBuilder()
      .loop(id = "L")
      .source(id = "r1", parent = "L")
      .source(id = "r2", parent = "L")
      .step(id = "join", parent = "L")
      .edge(from = "r1", to = "join")
      .build()

    assertEquals(listOf("r1", "r2"), LoopTopology.of(workflow = twoRoots).entryMembers(loopId = "L"))
  }

  @Test
  fun `a loop-free workflow has no loops and every node is top level`() {
    val topology = LoopTopology.of(workflow = LoopWorkflowBuilder().source(id = "a").step(id = "b").edge(from = "a", to = "b").build())

    assertFalse(topology.hasLoops())
    assertEquals(emptyList(), topology.enclosingLoops(nodeId = "b"))
  }

  @Test
  fun `malformed parents are ignored instead of looping forever`() {
    val malformed = LoopWorkflowBuilder()
      .loop(id = "L1", parent = "L2")
      .loop(id = "L2", parent = "L1")
      .step(id = "x", parent = "unknown")
      .source(id = "s")
      .step(id = "y", parent = "s")
      .build()
    val topology = LoopTopology.of(workflow = malformed)

    assertEquals(emptyList(), topology.enclosingLoops(nodeId = "L1"))
    assertEquals(emptyList(), topology.enclosingLoops(nodeId = "x"))
    assertEquals(emptyList(), topology.enclosingLoops(nodeId = "y"))
  }
}
