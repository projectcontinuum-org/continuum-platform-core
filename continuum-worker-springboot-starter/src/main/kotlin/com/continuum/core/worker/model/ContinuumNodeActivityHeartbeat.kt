package com.continuum.core.worker.model

import com.continuum.core.commons.prototol.progress.NodeProgress

data class ContinuumNodeActivityHeartbeat(
  val workflowId: String,
  val runId: String,
  val nodeId: String,
  val nodeProcess: NodeProgress
)