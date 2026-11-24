package org.projectcontinuum.core.commons.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
  name = "PortData",
  description = "One output of a node. A produced port references its Parquet file in object storage; a failed node has " +
    "the port `\$error` whose `data` is the error message. The system key `\$variable`, present for every finished " +
    "node (failed ones too), holds the node's flow variables as a JSON object in `data`: the ones it set plus the " +
    "default ones the worker exports for every run (`<nodeType>_<nodeId>.executionStatus`, `.durationMs`, ...)."
)
data class PortData @JsonCreator constructor(
  @field:Schema(description = "Whether the port was produced (`SUCCESS`), produced with warnings (`WARNING`) or is the error of a failed node (`FAILED`).", example = "SUCCESS")
  @param:JsonProperty("status") val status: PortDataStatus,
  @field:Schema(
    description = "Media type of `data`: `application/parquet` for a stored table, `text/plain` for the `\$error` message, " +
      "`application/json` for the `\$variable` flow variables.",
    type = "string",
    example = "application/parquet"
  )
  @param:JsonProperty("contentType") val contentType: Any,
  @field:Schema(description = "Columns of the table: one `{name, contentType}` entry per column. Empty for `\$error` and `\$variable`.")
  @param:JsonProperty("tableSpec") val tableSpec: List<Map<String, String>>,
  @field:Schema(
    description = "A string or a JSON object, depending on the port. For a table: a string, its location " +
      "`{remote}<runId>/<nodeId>/output.<port>.parquet` (read it with the node output endpoint). For `\$error`: a " +
      "string, the error message. For `\$variable`: a JSON object of flow variable names to values.",
    types = ["string", "object"],
    example = "{remote}0b7c6f0a-2a55-4f43-9a36-0c8f4d2b1e77/normalize/output.data.parquet"
  )
  @param:JsonProperty("data") val data: Any
)
