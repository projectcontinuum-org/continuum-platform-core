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
  @GetMapping("{workflowId}/nodes/{nodeId}/outputs/{outputId}")
  fun getData(
    @PathVariable
    workflowId: String,
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
      workflowId = workflowId,
      nodeId = nodeId,
      outputId = outputId,
      page = page,
      pageSize = pageSize
    )
  }
}