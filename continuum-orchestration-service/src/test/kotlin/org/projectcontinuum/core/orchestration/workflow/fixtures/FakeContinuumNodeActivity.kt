package org.projectcontinuum.core.orchestration.workflow.fixtures

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeActivityOutput
import org.projectcontinuum.core.commons.exception.PropertiesValidationException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.utils.DefaultFlowVariables
import org.projectcontinuum.core.commons.utils.FlowVariables
import org.projectcontinuum.core.commons.utils.ValidationHelper
import io.temporal.activity.Activity
import org.projectcontinuum.core.commons.protocol.progress.ContinuumNodeActivitySignal
import org.projectcontinuum.core.commons.protocol.progress.NodeProgress
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Configurable fake [IContinuumNodeActivity] for driving [ContinuumWorkflow][org.projectcontinuum.core.orchestration.workflow.ContinuumWorkflow]
 * tests without any real node business logic.
 *
 * Configured per-test via [successOutputs], [errorOutputs], [blockUntil], and exception-throwing maps before starting a workflow.
 */
class FakeContinuumNodeActivity : IContinuumNodeActivity {

  /** Outputs to return for a successful node run, keyed by [ContinuumWorkflowModel.Node.id]. */
  val successOutputs: MutableMap<String, Map<String, PortData>> = ConcurrentHashMap()

  /** Outputs containing the `$error` port to return instead of success, keyed by [ContinuumWorkflowModel.Node.id]. */
  val errorOutputs: MutableMap<String, Map<String, PortData>> = ConcurrentHashMap()

  /** If present for a graph node id, run() blocks (bounded) on this latch before returning. */
  val blockUntil: MutableMap<String, CountDownLatch> = ConcurrentHashMap()

  /** Graph node ids this fake was invoked for, in call order. */
  val invokedNodeIds: MutableList<String> = Collections.synchronizedList(mutableListOf())

  /** Throw this exception on every invocation for the given node ID. */
  val alwaysThrow: MutableMap<String, Exception> = ConcurrentHashMap()

  /** Function to determine if exception should be thrown, invoked before processing. */
  val throwOnAttempt: MutableMap<String, () -> Unit> = ConcurrentHashMap()

  /** Immediate exception to throw (for testing non-retryable scenarios). */
  val throwOnInvoke: MutableMap<String, () -> Unit> = ConcurrentHashMap()

  /** Track attempt count per node. */
  val attemptCount: MutableMap<String, AtomicInteger> = ConcurrentHashMap()

  /**
   * When set, nodes are checked like the real worker does (`ProcessNodeModel.run` → `ValidationHelper`): a node whose
   * properties violate the schema in this map (keyed by `data.nodeModel`) returns the `$error` output the worker
   * produces for a non-retriable `NodeRuntimeException`, instead of running.
   */
  val registeredSchemas: MutableMap<String, Map<String, Any>> = ConcurrentHashMap()

  /** Inputs the orchestrator sent, keyed by graph node id (last attempt wins). */
  val receivedInputs: MutableMap<String, Map<String, PortData>> = ConcurrentHashMap()

  /**
   * Flow variables a node sets, keyed by graph node id. Like the real worker, the fake returns only these under
   * `$variable` (never the inherited ones it received) and omits the key when there are none.
   */
  val flowVariablesToSet: MutableMap<String, Map<String, Any>> = ConcurrentHashMap()

  /**
   * When true, every returned output also carries the [DefaultFlowVariables] the real worker exports for each run,
   * built with the same names (`<nodeType>_<nodeId>.executionStatus`, ...), on success and on `$error` alike.
   * Off by default so the merge-rule tests can assert exact maps.
   */
  @Volatile
  var emitDefaultFlowVariables: Boolean = false

  /** Rows per output port reported in `outputCount` when [emitDefaultFlowVariables] is on; unset ports report 0. */
  val outputRowCounts: MutableMap<String, Map<String, Long>> = ConcurrentHashMap()

  override fun run(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>
  ): NodeActivityOutput {
    val startTimestamp = System.currentTimeMillis()
    val output = runWithoutDefaults(node = node, inputs = inputs)
    return if (emitDefaultFlowVariables) {
      withDefaultFlowVariables(node = node, inputs = inputs, output = output, startTimestamp = startTimestamp)
    } else {
      output
    }
  }

  /** Mirrors `ContinuumNodeActivity.withDefaultFlowVariables`: own variables plus defaults; a failure keeps only the defaults. */
  private fun withDefaultFlowVariables(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>,
    output: NodeActivityOutput,
    startTimestamp: Long
  ): NodeActivityOutput {
    val errorPort = output.outputs[IContinuumNodeActivity.NodeOutputSystemPort.ERROR.key]
    val failed = errorPort != null
    val dataInputPortIds = FlowVariables.withoutSystemPorts(inputs).keys
    val dataOutputPortIds = FlowVariables.withoutSystemPorts(output.outputs).keys
    val configuredOutputRows = outputRowCounts[node.id].orEmpty()
    val defaults = DefaultFlowVariables.build(
      prefix = DefaultFlowVariables.prefix(nodeModel = node.data.nodeModel, nodeId = node.id),
      failed = failed,
      errorMessage = errorPort?.data?.toString(),
      startTimestamp = startTimestamp,
      endTimestamp = System.currentTimeMillis(),
      inputRowCounts = if (node.data.inputs.isNullOrEmpty()) null else dataInputPortIds.associateWith { 0L },
      outputRowCounts = if (failed) null else dataOutputPortIds.associateWith { portId -> configuredOutputRows[portId] ?: 0L }
    )
    val ownVariables = if (failed) emptyMap() else FlowVariables.extract(output.outputs)
    return NodeActivityOutput(
      output.nodeId,
      output.outputs + (IContinuumNodeActivity.NodeOutputSystemPort.VARIABLE.key to FlowVariables.toPortData(ownVariables + defaults))
    )
  }

  private fun runWithoutDefaults(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>
  ): NodeActivityOutput {
    invokedNodeIds.add(node.id)
    receivedInputs[node.id] = inputs
    // Mirror the real worker: report BUSY as soon as the activity starts
    Activity.getExecutionContext().workflowClient
      .newWorkflowStub(
        IContinuumWorkflow::class.java,
        Activity.getExecutionContext().info.workflowId
      )
      .updateNodeProgressSignal(
        ContinuumNodeActivitySignal(
          nodeId = node.id,
          nodeProgress = NodeProgress(progressPercentage = 0),
          nodeStatus = ContinuumWorkflowModel.NodeStatus.BUSY
        )
      )
    attemptCount.computeIfAbsent(node.id) { AtomicInteger(0) }.incrementAndGet()

    registeredSchemas[node.data.nodeModel]?.let { schema ->
      val violations = ValidationHelper.findSchemaViolations(node.data.properties, schema)
      if (violations.isNotEmpty()) {
        return NodeActivityOutput(
          node.id,
          mapOf(
            IContinuumNodeActivity.NodeOutputSystemPort.ERROR.key to PortData(
              status = PortDataStatus.FAILED,
              contentType = "text/plain",
              tableSpec = emptyList(),
              data = "Invalid properties for node '${node.id}' (${node.data.nodeModel}): " +
                PropertiesValidationException.describe(violations)
            )
          )
        )
      }
    }

    // Check for immediate exception
    throwOnInvoke[node.id]?.also { 
      println("FakeContinuumNodeActivity: Throwing via throwOnInvoke for node ${node.id}")
      it.invoke() 
    }

    // Check for always-throw
    alwaysThrow[node.id]?.also { 
      println("FakeContinuumNodeActivity: Throwing via alwaysThrow for node ${node.id}: $it")
      throw it 
    }

    // Check for conditional throw
    throwOnAttempt[node.id]?.also { 
      println("FakeContinuumNodeActivity: Calling throwOnAttempt for node ${node.id}")
      it.invoke() 
    }

    blockUntil[node.id]?.await(5, TimeUnit.SECONDS)

    errorOutputs[node.id]?.let { return NodeActivityOutput(node.id, it) }
    val dataOutputs = successOutputs[node.id] ?: emptyMap()
    val ownFlowVariables = flowVariablesToSet[node.id].orEmpty()
    if (ownFlowVariables.isEmpty()) {
      return NodeActivityOutput(node.id, dataOutputs)
    }
    return NodeActivityOutput(
      node.id,
      dataOutputs + (IContinuumNodeActivity.NodeOutputSystemPort.VARIABLE.key to FlowVariables.toPortData(ownFlowVariables))
    )
  }
}
