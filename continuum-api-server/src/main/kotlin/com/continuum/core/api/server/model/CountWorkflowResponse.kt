package com.continuum.core.api.server.model

data class CountWorkflowResponse(
  val count: Int,
  val groups: List<Group>
) {
  data class Group(
    val name: String,
    val count: Int
  )
}