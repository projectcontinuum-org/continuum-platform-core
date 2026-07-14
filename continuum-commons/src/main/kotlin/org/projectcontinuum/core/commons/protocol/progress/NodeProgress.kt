package org.projectcontinuum.core.commons.protocol.progress

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class NodeProgress @JsonCreator constructor(
  @param:JsonProperty("progressPercentage") val progressPercentage: Int,
  @param:JsonProperty("message") val message: String? = null,
  @param:JsonProperty("stageStatus") val stageStatus: Map<String,StageStatus>? = null,
  @param:JsonProperty("stageDurationMs") val stageDurationMs: Long? = null,
  @param:JsonProperty("totalDurationMs") val totalDurationMs: Long? = null
)

enum class StageStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  FAILED,
  SKIPPED
}