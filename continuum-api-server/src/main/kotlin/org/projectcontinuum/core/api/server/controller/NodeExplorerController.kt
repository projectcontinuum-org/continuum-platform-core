package org.projectcontinuum.core.api.server.controller

import org.projectcontinuum.core.api.server.model.NodeExplorerTreeItem
import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/node-explorer")
class NodeExplorerController(
    private val nodeExplorerService: NodeExplorerService
) {

    @GetMapping("/children")
    fun getChildren(
        @RequestParam(required = false, defaultValue = "")
        parentId: String
    ): List<NodeExplorerTreeItem> {
        return nodeExplorerService.getChildren(parentId)
    }

    @GetMapping("/search")
    fun search(
        @RequestParam query: String
    ): List<NodeExplorerTreeItem> {
        return nodeExplorerService.search(query)
    }
}
