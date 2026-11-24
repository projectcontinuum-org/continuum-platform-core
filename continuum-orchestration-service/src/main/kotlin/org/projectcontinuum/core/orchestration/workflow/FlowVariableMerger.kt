package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.utils.FlowVariables

/**
 * Collects the flow variables a node inherits from all of its ancestors, for [WorkflowDagExecutor].
 *
 * Every completed node's `outputs["$variable"]` holds only the variables that node set itself. The variables
 * available after node P are its inherited ones overridden by its own:
 *
 * - `own(P)` = `outputs["$variable"]` of P (empty when P set none, or has not produced outputs)
 * - `available(P)` = `inherited(P)` + `own(P)` (P's own value wins)
 * - `inherited(N)` = fan-in merge of `available(parent)` over N's parent edges
 *
 * Fan-in merge: parent edges are ordered by the index of their target port in the node's declared inputs, then by
 * `targetHandle`, then by `source`. For each key the first value wins (lowest input port). An identical later value is
 * deduplicated silently; a different later value is a [Conflict].
 *
 * Pure: reads only the workflow model and the outputs map, so it is deterministic under Temporal replay.
 */
internal object FlowVariableMerger {

  /** Two parents of [targetNodeId] offered different values for [key]; the value from [keptFromNodeId] was used. */
  data class Conflict(
    val key: String,
    val targetNodeId: String,
    val keptFromNodeId: String,
    val ignoredFromNodeId: String
  )

  /** The inherited [variables] of one node, and the fan-in [conflicts] found at that node itself. */
  data class Result(
    val variables: Map<String, Any>,
    val conflicts: List<Conflict>
  )

  /** A merged variable value and the parent node it was taken from. */
  private data class Sourced(val value: Any, val fromNodeId: String)

  /**
   * Returns the flow variables [node] inherits from its ancestors, and the conflicts found when merging its own
   * parents. Conflicts deeper in the graph were already reported when those ancestors were dispatched.
   */
  fun collectInputVariables(
    continuumWorkflow: ContinuumWorkflowModel,
    node: ContinuumWorkflowModel.Node,
    nodeToOutputsMap: Map<String, Map<String, PortData>>
  ): Result {
    val nodesById = continuumWorkflow.nodes.associateBy { it.id }
    val availableByNodeId = HashMap<String, Map<String, Any>>()

    fun available(nodeId: String): Map<String, Any> {
      availableByNodeId[nodeId]?.let { return it }
      val ancestorNode = nodesById[nodeId]
      val inherited = if (ancestorNode == null) {
        emptyMap()
      } else {
        mergeParents(
          continuumWorkflow = continuumWorkflow,
          node = ancestorNode,
          availableOf = ::available
        ).first
      }
      val own = FlowVariables.extract(nodeToOutputsMap[nodeId].orEmpty())
      val result = inherited + own
      availableByNodeId[nodeId] = result
      return result
    }

    val (variables, conflicts) = mergeParents(
      continuumWorkflow = continuumWorkflow,
      node = node,
      availableOf = ::available
    )
    return Result(variables = variables, conflicts = conflicts)
  }

  /** Fan-in merge of the available variables of [node]'s parents, in deterministic input-port order. */
  private fun mergeParents(
    continuumWorkflow: ContinuumWorkflowModel,
    node: ContinuumWorkflowModel.Node,
    availableOf: (String) -> Map<String, Any>
  ): Pair<Map<String, Any>, List<Conflict>> {
    val inputPortIndex = node.data.inputs.orEmpty().keys.withIndex().associate { (index, portId) -> portId to index }
    val orderedParentIds = continuumWorkflow.getParentEdges(node)
      .sortedWith(
        compareBy<ContinuumWorkflowModel.Edge>(
          { edge -> inputPortIndex[edge.targetHandle] ?: Int.MAX_VALUE },
          { edge -> edge.targetHandle },
          { edge -> edge.source }
        )
      )
      .map { edge -> edge.source }
      .distinct()

    val merged = LinkedHashMap<String, Sourced>()
    val conflicts = mutableListOf<Conflict>()
    for (parentId in orderedParentIds) {
      for ((key, value) in availableOf(parentId)) {
        val existing = merged[key]
        when {
          existing == null -> merged[key] = Sourced(value = value, fromNodeId = parentId)
          existing.value == value -> Unit
          else -> conflicts += Conflict(
            key = key,
            targetNodeId = node.id,
            keptFromNodeId = existing.fromNodeId,
            ignoredFromNodeId = parentId
          )
        }
      }
    }
    return merged.mapValues { (_, sourced) -> sourced.value } to conflicts
  }
}
