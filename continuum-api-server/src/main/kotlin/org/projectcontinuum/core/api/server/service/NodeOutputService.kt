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
   * Returns one page of a node output, but only if [ownedBy] owns the run. Outputs are stored under the Temporal
   * **run id** (`{remote}<runId>/<nodeId>/output.<port>.parquet`), which is not the workflow id the API returns, so
   * ownership is looked up by `workflow_runs.run_id`. The check runs before the output is located, downloaded or
   * read, so nothing about another tenant's run (not even whether it exists) is revealed: an unowned and a
   * nonexistent run are both a [ResourceNotFoundException].
   */
  fun getOutput(
    ownedBy: String,
    runId: String,
    nodeId: String,
    outputId: String,
    page: Int,
    pageSize: Int
  ): Page<List<CellDto>> {
    if (!workflowRunRepository.existsByRunIdAndOwnedBy(runId, ownedBy)) {
      throw ResourceNotFoundException("Workflow run not found")
    }
    return nodeOutputRepository.getOutput(
      runId = runId,
      nodeId = nodeId,
      outputId = outputId,
      page = page,
      pageSize = pageSize
    )
  }
}
