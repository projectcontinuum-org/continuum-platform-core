package org.projectcontinuum.core.api.server.service

import org.projectcontinuum.core.api.server.exception.ResourceNotFoundException
import org.projectcontinuum.core.api.server.model.CellDto
import org.projectcontinuum.core.api.server.model.Page
import org.projectcontinuum.core.api.server.repository.NodeOutputRepository
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import org.springframework.stereotype.Service

@Service
class NodeOutputService(
  private val nodeOutputRepository: NodeOutputRepository,
  private val workflowRunRepository: WorkflowRunRepository
) {

  /**
   * Returns one page of a node output, but only if [ownedBy] owns the workflow run. The check runs before the
   * output is located, downloaded or read, so nothing about another tenant's run (not even whether it exists)
   * is revealed: an unowned and a nonexistent run are both a [ResourceNotFoundException].
   */
  fun getOutput(
    ownedBy: String,
    workflowId: String,
    nodeId: String,
    outputId: String,
    page: Int,
    pageSize: Int
  ): Page<List<CellDto>> {
    if (!workflowRunRepository.existsByWorkflowIdAndOwnedBy(workflowId, ownedBy)) {
      throw ResourceNotFoundException("Workflow run not found")
    }
    return nodeOutputRepository.getOutput(
      workflowId = workflowId,
      nodeId = nodeId,
      outputId = outputId,
      page = page,
      pageSize = pageSize
    )
  }
}
