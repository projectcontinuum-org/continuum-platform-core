package org.projectcontinuum.core.commons.protocol.progress

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "NodeProgress", description = "Progress a worker reported for a running node.")
data class NodeProgress @JsonCreator constructor(
  @field:Schema(description = "Progress of the node, in percent (0-100).", minimum = "0", maximum = "100", example = "40")
  @param:JsonProperty("progressPercentage") val progressPercentage: Int,
  @field:Schema(description = "Human-readable progress message.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "Uploading outputs")
  @param:JsonProperty("message") val message: String? = null,
  @field:Schema(description = "Status per stage: stage name → status.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "{\"Download inputs\": \"COMPLETED\", \"Execute\": \"IN_PROGRESS\"}")
  @param:JsonProperty("stageStatus") val stageStatus: Map<String,StageStatus>? = null,
  @field:Schema(description = "Duration of the current stage so far, in milliseconds.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "1250")
  @param:JsonProperty("stageDurationMs") val stageDurationMs: Long? = null,
  @field:Schema(description = "Time since the node started, in milliseconds.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, example = "4100")
  @param:JsonProperty("totalDurationMs") val totalDurationMs: Long? = null
)

@Schema(name = "NodeStageStatus", description = "Status of one stage of a running node.")
enum class StageStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  FAILED,
  SKIPPED
}