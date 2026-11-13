package org.projectcontinuum.core.api.server.controller

import org.projectcontinuum.core.api.server.model.CellDto
import org.projectcontinuum.core.api.server.model.Page
import org.projectcontinuum.core.api.server.service.NodeOutputService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/data")
class NodeOutputController(
  val nodeOutputService: NodeOutputService
) {
  /**
   * `runId` is the Temporal run id: the first segment of a stored output path (`{remote}<runId>/<nodeId>/...`),
   * which is what the workbench sends. It is not the workflow id returned by `POST /api/v1/workflow`.
   */
  @GetMapping("{runId}/nodes/{nodeId}/outputs/{outputId}")
  fun getData(
    @RequestHeader("x-continuum-user-id", required = false, defaultValue = "anonymous") ownedBy: String,
    @PathVariable
    runId: String,
    @PathVariable
    nodeId: String,
    @PathVariable
    outputId: String,
    @RequestParam(defaultValue = "0")
    page: Int,
    @RequestParam(defaultValue = "10")
    pageSize: Int
  ): Page<List<CellDto>> {
    return nodeOutputService.getOutput(
      ownedBy = ownedBy,
      runId = runId,
      nodeId = nodeId,
      outputId = outputId,
      page = page,
      pageSize = pageSize
    )
  }
}