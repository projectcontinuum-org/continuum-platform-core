package org.projectcontinuum.core.api.server.model

data class CellDto(
  val name: String,
  val value: ByteArray,
  val contentType: String,
)