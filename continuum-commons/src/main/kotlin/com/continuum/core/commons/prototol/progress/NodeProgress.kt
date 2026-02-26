package com.continuum.core.commons.prototol.progress

data class NodeProgress(
  val progressPercentage: Int,
  val message: String? = null,
  val stage: String? = null,
  val allStages: List<String>? = null,
  val stageDurationMs: Long? = null,
  val totalDurationMs: Long? = null
)