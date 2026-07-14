package org.projectcontinuum.core.commons.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class PortData @JsonCreator constructor(
  @param:JsonProperty("status") val status: PortDataStatus,
  @param:JsonProperty("contentType") val contentType: Any,
  @param:JsonProperty("tableSpec") val tableSpec: List<Map<String, String>>,
  @param:JsonProperty("data") val data: Any
)