package com.continuum.core.commons.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class PortData @JsonCreator constructor(
  @JsonProperty("status") val status: PortDataStatus,
  @JsonProperty("contentType") val contentType: Any,
  @JsonProperty("tableSpec") val tableSpec: List<Map<String, String>>,
  @JsonProperty("data") val data: Any
)