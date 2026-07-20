package org.projectcontinuum.core.orchestration.workflow.fixtures

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeActivityOutput
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
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

  override fun run(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>
  ): NodeActivityOutput {
    invokedNodeIds.add(node.id)
    attemptCount.computeIfAbsent(node.id) { AtomicInteger(0) }.incrementAndGet()

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
    return NodeActivityOutput(node.id, successOutputs[node.id] ?: emptyMap())
  }
}
