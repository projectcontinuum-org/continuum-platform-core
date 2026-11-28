package org.projectcontinuum.core.commons.validation.loop

import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.validation.GraphNode

/** A node as the loop structure checks see it: [parent] is the key of the loop it says it is inside. */
internal data class ScopedNode(val key: String, val nodeId: String, val type: String?, val parent: String?) {
  val isLoop: Boolean get() = nodeId == LoopNode.NODE_MODEL
}

/**
 * The loop scopes of a workflow being validated: which loop each node is directly inside, built once from the
 * `parent` pointers. Pure and immutable.
 *
 * Only well-formed links count: a `parent` that names an existing loop. A node with an unknown or non-loop parent,
 * and every loop on a parent cycle, is treated as top level here ([LoopStructureCheck] reports those problems), so the
 * other checks still see a tree and do not report follow-up errors.
 *
 * A scope is a loop key, or `null` for the top level.
 */
internal class LoopScopes(val nodes: List<ScopedNode>) {

  companion object {
    /**
     * The scopes of [graphNodes], limited to [acceptedKeys] (the keys the validator accepted; a duplicate key counts
     * once, with its first node).
     */
    fun of(graphNodes: List<GraphNode>, acceptedKeys: Set<String>): LoopScopes {
      val scopedNodes = graphNodes
        .filter { graphNode -> graphNode.key in acceptedKeys }
        .distinctBy { graphNode -> graphNode.key }
        .map { graphNode ->
          ScopedNode(
            key = graphNode.key!!,
            nodeId = graphNode.nodeId.orEmpty(),
            type = graphNode.type,
            parent = graphNode.parent
          )
        }
      return LoopScopes(nodes = scopedNodes)
    }
  }

  private val nodesByKey: Map<String, ScopedNode> = nodes.associateBy { node -> node.key }

  val loopKeys: Set<String> = nodes.filter { node -> node.isLoop }.map { node -> node.key }.toSet()

  /** Links whose parent exists and is a loop (they may still form a cycle). */
  private val wellFormedParentByKey: Map<String, String> = nodes
    .filter { node -> node.parent != null && node.parent in loopKeys }
    .associate { node -> node.key to node.parent!! }

  /** Every parent cycle, each in chain order starting at its first node in list order. */
  val parentCycles: List<List<String>> = ParentChainCycles.find(
    orderedKeys = nodes.map { node -> node.key },
    parentByKey = wellFormedParentByKey
  )

  private val keysOnParentCycles: Set<String> = parentCycles.flatten().toSet()

  private val parentByKey: Map<String, String> = wellFormedParentByKey.filterKeys { key -> key !in keysOnParentCycles }

  /** All scopes: the top level first, then each loop in list order. */
  val scopes: List<String?> = listOf<String?>(null) + nodes.filter { node -> node.isLoop }.map { node -> node.key }

  fun isLoop(key: String): Boolean = key in loopKeys

  fun nodeOf(key: String): ScopedNode? = nodesByKey[key]

  /** The innermost loop [key] is inside, or null at the top level. */
  fun parentOf(key: String): String? = parentByKey[key]

  /** The nodes directly inside [scope], in list order. */
  fun directChildren(scope: String?): List<String> =
    nodes.map { node -> node.key }.filter { key -> parentOf(key = key) == scope }

  /**
   * The ancestor of [key] (or [key] itself) that sits directly inside [scope], or null when [key] is not inside
   * [scope] at all. Every node is inside the top level (`scope == null`).
   */
  fun directChildIn(scope: String?, key: String): String? {
    var current: String = key
    val visited = mutableSetOf<String>()
    while (visited.add(current)) {
      val parent = parentOf(key = current)
      if (parent == scope) return current
      current = parent ?: return null
    }
    return null
  }
}

/** Finds cycles in a parent-pointer map without recursion. */
internal object ParentChainCycles {

  fun find(orderedKeys: List<String>, parentByKey: Map<String, String>): List<List<String>> {
    val cycles = mutableListOf<List<String>>()
    val settled = mutableSetOf<String>()
    for (startKey in orderedKeys) {
      if (startKey in settled) continue
      val chain = mutableListOf<String>()
      val positionInChain = mutableMapOf<String, Int>()
      var current: String? = startKey
      while (current != null && current !in settled && current !in positionInChain) {
        positionInChain[current] = chain.size
        chain += current
        current = parentByKey[current]
      }
      if (current != null && current in positionInChain) {
        cycles += chain.subList(positionInChain.getValue(current), chain.size).toList()
      }
      settled += chain
    }
    return cycles.map { cycle -> rotateToFirstInOrder(cycle = cycle, orderedKeys = orderedKeys) }
  }

  private fun rotateToFirstInOrder(cycle: List<String>, orderedKeys: List<String>): List<String> {
    val firstKey = orderedKeys.first { key -> key in cycle }
    val firstIndex = cycle.indexOf(firstKey)
    return cycle.subList(firstIndex, cycle.size) + cycle.subList(0, firstIndex)
  }
}
