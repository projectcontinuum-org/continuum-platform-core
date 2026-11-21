package org.projectcontinuum.core.api.server.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "TableCell", description = "One cell of a node output table row.")
data class CellDto(
  @field:Schema(description = "Column name.", example = "clean")
  val name: String,
  @field:Schema(
    description = "Raw cell bytes, base64-encoded. Decode them according to `contentType` (e.g. UTF-8 text for `text/plain`).",
    type = "string",
    format = "byte",
    example = "aGVsbG8gd29ybGQ="
  )
  val value: ByteArray,
  @field:Schema(description = "Media type of the cell value, as written by the node.", example = "text/plain")
  val contentType: String,
)
