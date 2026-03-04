package com.continuum.core.commons.prototol.progress

data class ContinuumNodeActivitySignal(
  val nodeId: String,
  val nodeProgress: NodeProgress
)