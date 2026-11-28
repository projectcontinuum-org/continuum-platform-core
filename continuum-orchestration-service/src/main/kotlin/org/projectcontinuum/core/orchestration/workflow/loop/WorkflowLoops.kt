package org.projectcontinuum.core.orchestration.workflow.loop

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.loopcondition.LoopConditionValidators

/**
 * What the DAG executor needs from the loops of a run: the readiness [gate], the variables carried into each running
 * iteration, and [advance] on every pass. [NONE] is a workflow without loops: the gate changes nothing and [advance]
 * never does anything, so such a run takes exactly the path it took before loops existed.
 */
interface WorkflowLoops {
  val topology: LoopTopology

  fun gate(): LoopReadinessGate

  fun carriedByLoopId(): Map<String, Map<String, Any>>

  /** The running iteration of the innermost loop around [nodeId], or null when it is not inside an iterating loop. */
  fun iterationOf(nodeId: String): Int?

  /** Starts, ends and restarts loops; true when anything changed (new nodes may be ready). */
  fun advance(): Boolean

  object NONE : WorkflowLoops {
    override val topology: LoopTopology = LoopTopology.NONE
    override fun gate(): LoopReadinessGate = LoopReadinessGate.NONE
    override fun carriedByLoopId(): Map<String, Map<String, Any>> = emptyMap()
    override fun iterationOf(nodeId: String): Int? = null
    override fun advance(): Boolean = false
  }
}

/** Builds the [WorkflowLoops] of a run. The production collaborators are the defaults; tests replace single ones. */
class WorkflowLoopsFactory(
  private val startCheck: LoopStartCheck = LoopStartCheck(
    validator = LoopConditionValidators.default,
    recorder = LoopStartCheckRecorder.TEMPORAL
  ),
  private val conditionCheck: LoopConditionCheck = LoopConditionCheck(recorder = ConditionOutcomeRecorder.TEMPORAL),
  private val historyGuard: () -> LoopHistoryGuard = { LoopHistoryGuard() }
) {

  fun create(
    workflow: ContinuumWorkflowModel,
    nodeToOutputsMap: MutableMap<String, Map<String, PortData>>,
    nodeErrorsMap: MutableMap<String, Map<String, PortData>>,
    setNodeStatus: (ContinuumWorkflowModel.Node, ContinuumWorkflowModel.NodeStatus) -> Unit
  ): WorkflowLoops {
    val topology = LoopTopology.of(workflow = workflow)
    if (!topology.hasLoops()) return WorkflowLoops.NONE
    val context = LoopRunContext(
      workflow = workflow,
      topology = topology,
      nodeToOutputsMap = nodeToOutputsMap,
      nodeErrorsMap = nodeErrorsMap,
      setNodeStatus = setNodeStatus
    )
    val runner = LoopRunner(
      context = context,
      evaluator = { LoopConditionEvaluators.current },
      startCheck = startCheck,
      conditionCheck = conditionCheck,
      historyGuard = historyGuard()
    )
    return object : WorkflowLoops {
      override val topology: LoopTopology = topology
      override fun gate(): LoopReadinessGate = runner.gate()
      override fun carriedByLoopId(): Map<String, Map<String, Any>> = runner.carriedByLoopId()
      override fun iterationOf(nodeId: String): Int? = runner.iterationOf(nodeId = nodeId)
      override fun advance(): Boolean = runner.advance()
    }
  }

  companion object {
    /** The factory the orchestrator uses; tests may install a different one (for example a tiny history guard). */
    @Volatile
    var current: WorkflowLoopsFactory = WorkflowLoopsFactory()
  }
}
