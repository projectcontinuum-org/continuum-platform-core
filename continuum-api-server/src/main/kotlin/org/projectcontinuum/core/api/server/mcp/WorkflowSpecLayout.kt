package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

/**
 * Canvas layout of a built workflow, for [WorkflowSpecBuilder]. Pure: the result depends only on the inputs.
 *
 * Every scope (the top level and each loop) is laid out on its own, innermost loops first, so a loop's size is known
 * before its parent scope is placed. In a scope, its direct children (nodes and nested loops) go into columns by
 * longest-path depth over the edges between them; an edge between nodes deeper inside counts for the child that
 * contains them, and an edge inside one child does not count. A column is as wide as its widest box, a row as tall as
 * its box: with only plain nodes this is exactly the old fixed grid ([X_STEP] by [Y_STEP]).
 *
 * A loop box holds its members with [LOOP_SIDE_PADDING] on the left, right and bottom and a [LOOP_HEADER_HEIGHT]
 * header on top, and is at least [LOOP_MIN_WIDTH] x [LOOP_MIN_HEIGHT] (the editor's default size). A member's position
 * is relative to its loop, as React Flow expects for `parentNode`; [Placement.absolutePosition] is the canvas
 * position. [Result.orderedKeys] keeps the spec order but moves every loop in front of its first member, because
 * React Flow needs a parent before its children.
 *
 * [parentByKey] must be well formed (every parent a loop key, no cycles): the builder only lays out validated specs.
 */
internal class WorkflowSpecLayout(
  private val keys: List<String>,
  private val parentByKey: Map<String, String>,
  private val loopKeys: Set<String>,
  private val edges: List<Pair<String, String>>
) {

  companion object {
    const val NODE_WIDTH = 224
    const val NODE_HEIGHT = 100
    const val X_STEP = 330.0
    const val Y_STEP = 160.0
    const val LOOP_SIDE_PADDING = 40
    const val LOOP_HEADER_HEIGHT = 60
    const val LOOP_MIN_WIDTH = 520
    const val LOOP_MIN_HEIGHT = 320

    /** Gap between two columns (or rows): what the fixed grid leaves between two plain nodes. */
    private const val COLUMN_GAP = X_STEP - NODE_WIDTH
    private const val ROW_GAP = Y_STEP - NODE_HEIGHT
  }

  data class Placement(
    val position: ContinuumWorkflowModel.Position,
    val absolutePosition: ContinuumWorkflowModel.Position,
    val width: Int,
    val height: Int
  )

  data class Result(val orderedKeys: List<String>, val placements: Map<String, Placement>)

  private data class Size(val width: Int, val height: Int)

  /** Positions of a scope's direct children, relative to the scope's content origin, and the content's extent. */
  private data class ScopeLayout(val offsets: Map<String, Pair<Double, Double>>, val contentWidth: Double, val contentHeight: Double)

  private val scopeLayouts = HashMap<String?, ScopeLayout>()
  private val sizes = HashMap<String, Size>()

  fun compute(): Result {
    val relativePositions = HashMap<String, ContinuumWorkflowModel.Position>()
    for (scope in listOf<String?>(null) + keys.filter { key -> key in loopKeys }) {
      val contentOrigin = if (scope == null) 0.0 to 0.0 else LOOP_SIDE_PADDING.toDouble() to LOOP_HEADER_HEIGHT.toDouble()
      scopeLayout(scope = scope).offsets.forEach { (child, offset) ->
        relativePositions[child] = ContinuumWorkflowModel.Position(
          x = contentOrigin.first + offset.first,
          y = contentOrigin.second + offset.second
        )
      }
    }
    val placements = keys.associateWith { key ->
      val size = sizeOf(key = key)
      Placement(
        position = relativePositions.getValue(key),
        absolutePosition = absolutePosition(key = key, relativePositions = relativePositions),
        width = size.width,
        height = size.height
      )
    }
    return Result(orderedKeys = parentFirstOrder(), placements = placements)
  }

  private fun childrenOf(scope: String?): List<String> = keys.filter { key -> parentByKey[key] == scope }

  /** [key] itself or its ancestor that sits directly in [scope]; null when [key] is not inside [scope]. */
  private fun directChildIn(scope: String?, key: String): String? {
    var current = key
    while (true) {
      val parent = parentByKey[current]
      if (parent == scope) return current
      current = parent ?: return null
    }
  }

  private fun sizeOf(key: String): Size = sizes.getOrPut(key) {
    if (key !in loopKeys) return@getOrPut Size(width = NODE_WIDTH, height = NODE_HEIGHT)
    val content = scopeLayout(scope = key)
    val hasMembers = content.offsets.isNotEmpty()
    val contentWidth = if (hasMembers) content.contentWidth else 0.0
    val contentHeight = if (hasMembers) content.contentHeight else 0.0
    Size(
      width = maxOf(LOOP_MIN_WIDTH, (LOOP_SIDE_PADDING + contentWidth + LOOP_SIDE_PADDING).toInt()),
      height = maxOf(LOOP_MIN_HEIGHT, (LOOP_HEADER_HEIGHT + contentHeight + LOOP_SIDE_PADDING).toInt())
    )
  }

  private fun scopeLayout(scope: String?): ScopeLayout = scopeLayouts.getOrPut(scope) {
    val children = childrenOf(scope = scope)
    val depthByChild = depthsInScope(scope = scope, children = children)
    val columns = children.groupBy { child -> depthByChild.getValue(child) }.toSortedMap()
    val offsets = LinkedHashMap<String, Pair<Double, Double>>()
    var columnX = 0.0
    var contentWidth = 0.0
    var contentHeight = 0.0
    for ((_, columnChildren) in columns) {
      var rowY = 0.0
      var widestInColumn = 0
      for (child in columnChildren) {
        val size = sizeOf(key = child)
        offsets[child] = columnX to rowY
        contentHeight = maxOf(contentHeight, rowY + size.height)
        rowY += maxOf(Y_STEP, size.height + ROW_GAP)
        widestInColumn = maxOf(widestInColumn, size.width)
      }
      contentWidth = maxOf(contentWidth, columnX + widestInColumn)
      columnX += maxOf(X_STEP, widestInColumn + COLUMN_GAP)
    }
    ScopeLayout(offsets = offsets, contentWidth = contentWidth, contentHeight = contentHeight)
  }

  /** Longest-path depth of each child of [scope] over the edges mapped to the scope's direct children. */
  private fun depthsInScope(scope: String?, children: List<String>): Map<String, Int> {
    val scopeEdges = edges.mapNotNull { (from, to) ->
      val fromChild = directChildIn(scope = scope, key = from)
      val toChild = directChildIn(scope = scope, key = to)
      if (fromChild == null || toChild == null || fromChild == toChild) null else fromChild to toChild
    }.distinct()
    val depth = children.associateWith { 0 }.toMutableMap()
    val indegree = children.associateWith { 0 }.toMutableMap()
    val outgoing = children.associateWith { mutableListOf<String>() }
    for ((from, to) in scopeEdges) {
      outgoing.getValue(from).add(to)
      indegree[to] = indegree.getValue(to) + 1
    }
    val queue = ArrayDeque(children.filter { child -> indegree.getValue(child) == 0 })
    while (queue.isNotEmpty()) {
      val child = queue.removeFirst()
      for (next in outgoing.getValue(child)) {
        depth[next] = maxOf(depth.getValue(next), depth.getValue(child) + 1)
        indegree[next] = indegree.getValue(next) - 1
        if (indegree.getValue(next) == 0) queue.addLast(next)
      }
    }
    return depth
  }

  private fun absolutePosition(
    key: String,
    relativePositions: Map<String, ContinuumWorkflowModel.Position>
  ): ContinuumWorkflowModel.Position {
    var x = 0.0
    var y = 0.0
    var current: String? = key
    while (current != null) {
      val relative = relativePositions.getValue(current)
      x += relative.x
      y += relative.y
      current = parentByKey[current]
    }
    return ContinuumWorkflowModel.Position(x = x, y = y)
  }

  /** Spec order, except that a loop (and the loops around it) is moved in front of its first member. */
  private fun parentFirstOrder(): List<String> {
    val ordered = LinkedHashSet<String>()
    for (key in keys) {
      val outermostFirst = generateSequence(parentByKey[key]) { loopKey -> parentByKey[loopKey] }.toList().asReversed()
      ordered += outermostFirst
      ordered += key
    }
    return ordered.toList()
  }
}
