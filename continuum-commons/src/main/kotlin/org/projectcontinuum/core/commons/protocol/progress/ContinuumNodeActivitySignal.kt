package org.projectcontinuum.core.commons.protocol.progress

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

data class ContinuumNodeActivitySignal (
  val nodeId: String = "",
  val nodeProgress: NodeProgress,
  /** When set, asks the workflow to move a SCHEDULED node to this status (only BUSY is honoured). */
  val nodeStatus: ContinuumWorkflowModel.NodeStatus? = null
)