package com.continuum.core.worker.model

import com.continuum.core.commons.prototol.progress.NodeProgress

/**
 * Data class representing a heartbeat from a Continuum node activity.
 *
 * This class is used to communicate the execution progress of a node activity
 * back to the Temporal workflow. Heartbeats are sent periodically during
 * long-running activities to indicate that the activity is still alive and
 * making progress.
 *
 * ## Usage
 * Heartbeats are typically sent from within [ContinuumNodeActivity] during
 * node execution to report progress to the parent workflow.
 *
 * @property workflowId The unique identifier of the Temporal workflow execution
 * @property runId The unique identifier of the current workflow run
 * @property nodeId The unique identifier of the node being executed
 * @property nodeProcess The current progress information for the node
 * @author Continuum Team
 * @since 1.0.0
 * @see NodeProgress
 */
data class ContinuumNodeActivityHeartbeat(
  val workflowId: String,
  val runId: String,
  val nodeId: String,
  val nodeProcess: NodeProgress
)