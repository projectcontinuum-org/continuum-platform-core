package org.projectcontinuum.core.orchestration.workflow

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.utils.FlowVariables
import org.projectcontinuum.core.orchestration.workflow.loop.LoopTopology

/**
 * Collects the flow variables a node inherits from all of its ancestors, for [WorkflowDagExecutor].
 *
 * Every completed node's `outputs["$variable"]` holds only that node's own variables (what it set, plus the worker's
 * default variables for its run), never the inherited ones. The variables
 * available after node P are its inherited ones overridden by its own:
 *
 * - `own(P)` = `outputs["$variable"]` of P (empty when P has not produced outputs, or ran on a worker older than 0.0.23)
 * - `available(P)` = `inherited(P)` + `own(P)` (P's own value wins)
 * - `inherited(N)` = fan-in merge of `available(parent)` over N's parent edges, then, for every loop around N in which
 *   N is an entry member (outermost loop first), overridden by that loop's carried variables
 *
 * Fan-in merge: parent edges are ordered by the index of their target port in the node's declared inputs, then by
 * `targetHandle`, then by `source`. For each key the first value wins (lowest input port). A later value that is the
 * same JSON value ([FlowVariables.valuesEqual]: numeric-aware, so `5` and `5L` or `1.0` and `1` match) is
 * deduplicated silently; a different later value is a [Conflict].
 *
 * Loops: what the members of a loop set in one iteration reaches the next iteration's entry members (the members with
 * no incoming edge from inside the loop) and overrides what they get from outside. The loop runner keeps these
 * carried variables per loop ([org.projectcontinuum.core.orchestration.workflow.loop.LoopCarriedVariables]) and
 * passes them in `carriedByLoopId`; they are empty before the first iteration. The condition of a loop reads
 * [endOfIterationVariables]. Without loops (the default
 * [LoopTopology.NONE]) the result is exactly the fan-in rule above. The workbench's `FlowVariables.ts`
 * (`inheritedFlowVariables`) mirrors this rule; `src/test/resources/flow-variables/loop-carry-cases.json` is the case
 * list both sides are tested against, so change all three together.
 *
 * Pure: reads only the workflow model, the outputs map and the carried maps, so it is deterministic under Temporal
 * replay.
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
   *
   * [loopTopology] and [carriedByLoopId] (loop id to the variables its members carried out of the previous
   * iterations) add the loop rule; leave both at their defaults for a workflow without loops.
   */
  fun collectInputVariables(
    continuumWorkflow: ContinuumWorkflowModel,
    node: ContinuumWorkflowModel.Node,
    nodeToOutputsMap: Map<String, Map<String, PortData>>,
    loopTopology: LoopTopology = LoopTopology.NONE,
    carriedByLoopId: Map<String, Map<String, Any>> = emptyMap()
  ): Result {
    val context = MergeContext(
      continuumWorkflow = continuumWorkflow,
      nodeToOutputsMap = nodeToOutputsMap,
      loopTopology = loopTopology,
      carriedByLoopId = carriedByLoopId
    )
    return context.inherited(node = node)
  }

  /**
   * The flow variables a loop's condition reads after an iteration: the merge of the variables available from the
   * loop's outside parents (the sources of the edges entering the loop), overridden by the loop's carried variables.
   * For a check before the first iteration the carried variables are empty, so it is the outside part only.
   *
   * When several outside parents set the same name, the entering edges are ordered by the run order of their target
   * member in the loop ([LoopTopology.topologicalOrder]), then by the target's input port index, then `targetHandle`,
   * then source id, and the first value per name wins. A single fan-in order is defined only per node; this extends it
   * to the loop so the condition always reads the variable of the parent that feeds the earliest member.
   */
  fun endOfIterationVariables(
    continuumWorkflow: ContinuumWorkflowModel,
    loopId: String,
    nodeToOutputsMap: Map<String, Map<String, PortData>>,
    loopTopology: LoopTopology,
    carriedByLoopId: Map<String, Map<String, Any>>
  ): Result {
    val context = MergeContext(
      continuumWorkflow = continuumWorkflow,
      nodeToOutputsMap = nodeToOutputsMap,
      loopTopology = loopTopology,
      carriedByLoopId = carriedByLoopId
    )
    val runPositionByNodeId = loopTopology.topologicalOrder(loopId = loopId).withIndex()
      .associate { (index, nodeId) -> nodeId to index }
    val orderedOutsideParentIds = loopTopology.externalIncomingEdges(loopId = loopId)
      .sortedWith(
        compareBy<ContinuumWorkflowModel.Edge>(
          { edge -> runPositionByNodeId[edge.target] ?: Int.MAX_VALUE },
          { edge -> context.inputPortIndex(nodeId = edge.target, portId = edge.targetHandle) },
          { edge -> edge.targetHandle },
          { edge -> edge.source }
        )
      )
      .map { edge -> edge.source }
      .distinct()
    val (outsideVariables, conflicts) = context.fanIn(targetNodeId = loopId, orderedParentIds = orderedOutsideParentIds)
    val carried = carriedByLoopId[loopId].orEmpty()
    return Result(variables = outsideVariables + carried, conflicts = conflicts)
  }

  /** One merge: the inputs and the memo of `available(P)` per node. */
  private class MergeContext(
    private val continuumWorkflow: ContinuumWorkflowModel,
    private val nodeToOutputsMap: Map<String, Map<String, PortData>>,
    private val loopTopology: LoopTopology,
    private val carriedByLoopId: Map<String, Map<String, Any>>
  ) {
    private val nodesById = continuumWorkflow.nodes.associateBy { node -> node.id }
    private val availableByNodeId = HashMap<String, Map<String, Any>>()

    fun inherited(node: ContinuumWorkflowModel.Node): Result {
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
      val (fannedIn, conflicts) = fanIn(targetNodeId = node.id, orderedParentIds = orderedParentIds)
      return Result(variables = withCarriedVariables(nodeId = node.id, fannedIn = fannedIn), conflicts = conflicts)
    }

    fun inputPortIndex(nodeId: String, portId: String): Int {
      val inputPortIds = nodesById[nodeId]?.data?.inputs.orEmpty().keys.toList()
      val index = inputPortIds.indexOf(portId)
      return if (index >= 0) index else Int.MAX_VALUE
    }

    /** First value per key wins, in [orderedParentIds] order; a different later value is a [Conflict]. */
    fun fanIn(targetNodeId: String, orderedParentIds: List<String>): Pair<Map<String, Any>, List<Conflict>> {
      val merged = LinkedHashMap<String, Sourced>()
      val conflicts = mutableListOf<Conflict>()
      for (parentId in orderedParentIds) {
        for ((key, value) in available(nodeId = parentId)) {
          val existing = merged[key]
          when {
            existing == null -> merged[key] = Sourced(value = value, fromNodeId = parentId)
            FlowVariables.valuesEqual(first = existing.value, second = value) -> Unit
            else -> conflicts += Conflict(
              key = key,
              targetNodeId = targetNodeId,
              keptFromNodeId = existing.fromNodeId,
              ignoredFromNodeId = parentId
            )
          }
        }
      }
      return merged.mapValues { (_, sourced) -> sourced.value } to conflicts
    }

    /** `available(P)` = `inherited(P)` + `own(P)`, memoized. */
    private fun available(nodeId: String): Map<String, Any> {
      availableByNodeId[nodeId]?.let { memoized -> return memoized }
      val ancestorNode = nodesById[nodeId]
      val inherited = if (ancestorNode == null) emptyMap() else inherited(node = ancestorNode).variables
      val own = FlowVariables.extract(nodeToOutputsMap[nodeId].orEmpty())
      val result = inherited + own
      availableByNodeId[nodeId] = result
      return result
    }

    /** Applies the carried variables of every loop around [nodeId] that it is an entry member of, outermost first. */
    private fun withCarriedVariables(nodeId: String, fannedIn: Map<String, Any>): Map<String, Any> {
      if (!loopTopology.hasLoops()) return fannedIn
      return loopTopology.enclosingLoops(nodeId = nodeId)
        .filter { loopId -> loopTopology.isEntryMember(nodeId = nodeId, loopId = loopId) }
        .fold(initial = fannedIn) { variables, loopId -> variables + carriedByLoopId[loopId].orEmpty() }
    }
  }
}
