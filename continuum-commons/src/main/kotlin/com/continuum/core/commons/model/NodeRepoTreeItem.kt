package com.continuum.core.commons.model

data class NodeRepoTreeItem(
  val id: String,
  val name: String,
  val nodeInfo: ContinuumWorkflowModel.NodeData? = null,
  val children: MutableList<NodeRepoTreeItem>? = null
)