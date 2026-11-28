package org.projectcontinuum.core.commons.validation.loop

import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.validation.WorkflowIssue

/**
 * The node-level loop rules of [org.projectcontinuum.core.commons.validation.WorkflowValidator]: the
 * React Flow `type` matches the loop nodeId, every `parent` names an existing loop, parent chains have no cycle, and a
 * loop without members is a warning. Pure: returns the issues, changes nothing. Each problem is one issue with `node`
 * set.
 */
internal object LoopStructureRules {

  fun errors(scopes: LoopScopes): List<WorkflowIssue> {
    val typeErrors = scopes.nodes.mapNotNull { node -> typeMismatch(node = node) }
    val parentErrors = scopes.nodes.mapNotNull { node -> invalidParent(node = node, scopes = scopes) }
    val cycleErrors = scopes.parentCycles.map { cycle -> parentCycle(cycle = cycle) }
    return typeErrors + parentErrors + cycleErrors
  }

  fun warnings(scopes: LoopScopes): List<WorkflowIssue> =
    scopes.loopKeys
      .filter { loopKey -> scopes.directChildren(scope = loopKey).isEmpty() && loopKey !in scopes.parentCycles.flatten() }
      .map { loopKey -> WorkflowIssue(message = "Loop '$loopKey' has no nodes inside it.", node = loopKey) }

  private fun typeMismatch(node: ScopedNode): WorkflowIssue? {
    val declaredType = node.type ?: return null
    val declaresLoopType = declaredType == LoopNode.NODE_TYPE
    if (declaresLoopType == node.isLoop) return null
    val message = if (declaresLoopType) {
      "Node '${node.key}': type '${LoopNode.NODE_TYPE}' is only for the built-in loop (nodeId '${LoopNode.NODE_MODEL}'), " +
        "but this node has nodeId '${node.nodeId}'. Use type 'BaseNode' for registered nodes."
    } else {
      "Node '${node.key}': the built-in loop (nodeId '${LoopNode.NODE_MODEL}') needs type '${LoopNode.NODE_TYPE}', " +
        "not '$declaredType'."
    }
    return WorkflowIssue(message = message, node = node.key)
  }

  private fun invalidParent(node: ScopedNode, scopes: LoopScopes): WorkflowIssue? {
    val parentKey = node.parent ?: return null
    val parentNode = scopes.nodeOf(key = parentKey)
    val message = when {
      parentNode == null ->
        "Node '${node.key}': its parent '$parentKey' is not a node of this workflow. Set parent to the key of the loop " +
          "the node is inside, or remove it."
      !parentNode.isLoop ->
        "Node '${node.key}': its parent '$parentKey' is not a loop. Only a loop (nodeId '${LoopNode.NODE_MODEL}') can " +
          "contain nodes."
      else -> return null
    }
    return WorkflowIssue(message = message, node = node.key)
  }

  private fun parentCycle(cycle: List<String>): WorkflowIssue {
    val chainText = (cycle + cycle.first()).joinToString(separator = " -> ") { key -> "'$key'" }
    return WorkflowIssue(
      message = "Node '${cycle.first()}': its parent chain $chainText is a cycle. A loop cannot be inside itself.",
      node = cycle.first()
    )
  }
}
