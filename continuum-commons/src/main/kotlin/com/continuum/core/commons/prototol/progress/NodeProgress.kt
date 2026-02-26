package com.continuum.core.commons.prototol.progress

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class NodeProgress @JsonCreator constructor(
  @JsonProperty("progressPercentage") val progressPercentage: Int,
  @JsonProperty("message") val message: String? = null,
  @JsonProperty("stage") val stage: String? = null,
  @JsonProperty("allStages") val allStages: List<String>? = null,
  @JsonProperty("stageDurationMs") val stageDurationMs: Long? = null,
  @JsonProperty("totalDurationMs") val totalDurationMs: Long? = null
)