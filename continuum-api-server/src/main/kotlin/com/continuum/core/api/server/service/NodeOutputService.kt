package com.continuum.core.api.server.service

import com.continuum.core.api.server.model.CellDto
import com.continuum.core.api.server.model.Page
import com.continuum.core.api.server.repository.NodeOutputRepository
import org.springframework.stereotype.Service

@Service
class NodeOutputService(
  private val nodeOutputRepository: NodeOutputRepository
) {

  fun getOutput(
    workflowId: String,
    nodeId: String,
    outputId: String,
    page: Int,
    pageSize: Int
  ): Page<List<CellDto>> {
    return nodeOutputRepository.getOutput(
      workflowId = workflowId,
      nodeId = nodeId,
      outputId = outputId,
      page = page,
      pageSize = pageSize
    )
  }
}