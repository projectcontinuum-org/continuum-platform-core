package org.projectcontinuum.core.commons.protocol.progress

data class ContinuumNodeActivitySignal (
  val nodeId: String = "",
  val nodeProgress: NodeProgress
)