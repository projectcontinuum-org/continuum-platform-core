package com.continuum.core.worker.controller

import com.continuum.core.commons.model.NodeRepoTreeItem
import com.continuum.core.commons.node.ContinuumNodeModel
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/node-repo")
class NodeRepositoryController(
    private val nodeModels: ObjectProvider<ContinuumNodeModel>
) {
    private val treeRoots = mutableListOf<NodeRepoTreeItem>()
    @PostConstruct
    fun loadNodes() {
        nodeModels.forEach {
            it.categories.forEach { category ->
                val categoryPath = category.split("/")
                addNodeModelToCategories(treeRoots, it, categoryPath.toMutableList())
            }
        }
    }

    @GetMapping
    fun getNodes(): List<NodeRepoTreeItem> {
        return treeRoots
    }

    private fun addNodeModelToCategories(
        treeRoots: MutableList<NodeRepoTreeItem>,
        nodeModel: ContinuumNodeModel,
        categoryPath: MutableList<String>
    ) {
        if (categoryPath.isEmpty()) {
            treeRoots.add(
                NodeRepoTreeItem(
                    id = nodeModel.javaClass.name,
                    name = nodeModel.javaClass.simpleName,
                    nodeInfo = nodeModel.metadata
                )
            )
        } else {
            var category = treeRoots.filter { it.name == categoryPath[0] }
            if (category.isEmpty()) {
                treeRoots.add(
                    NodeRepoTreeItem(
                        id = categoryPath[0],
                        name = categoryPath[0],
                        children = mutableListOf()
                    )
                )
            }
            category = treeRoots.filter { it.name == categoryPath[0] }
            categoryPath.removeAt(0)
            addNodeModelToCategories(category[0].children!!, nodeModel, categoryPath)
        }
    }
}