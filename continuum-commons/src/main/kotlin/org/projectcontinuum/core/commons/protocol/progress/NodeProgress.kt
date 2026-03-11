package org.projectcontinuum.core.commons.protocol.progress

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class NodeProgress @JsonCreator constructor(
  @JsonProperty("progressPercentage") val progressPercentage: Int,
  @JsonProperty("message") val message: String? = null,
  @JsonProperty("stageStatus") val stageStatus: Map<String,StageStatus>? = null,
  @JsonProperty("stageDurationMs") val stageDurationMs: Long? = null,
  @JsonProperty("totalDurationMs") val totalDurationMs: Long? = null
)

enum class StageStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  FAILED,
  SKIPPED
}