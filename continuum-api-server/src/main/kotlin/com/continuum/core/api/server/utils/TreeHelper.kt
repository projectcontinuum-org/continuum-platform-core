package com.continuum.core.api.server.utils

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.ExecutionStatus
import com.continuum.core.commons.model.PortData
import io.temporal.api.enums.v1.EventType

class TreeHelper {
    companion object {
        fun addItemToParent(
            treeRoots: MutableList<TreeItem<Execution>>,
            execution: Execution,
            categoryPath: MutableList<String>,
            maxChildren: Int = 10
        ) {
            if (categoryPath.isEmpty()) {
                // only keep first 5 elements in treeRoots
                if (treeRoots.size > maxChildren) {
                    treeRoots.subList(maxChildren, treeRoots.size).clear()
                }
                val index = treeRoots.indexOfFirst { it.id == execution.id }
                if (index != -1) {
                    treeRoots[index] = TreeItem(
                        id = execution.id,
                        name = java.util.Date(execution.createdAtTimestampUtc).toString(),
                        itemInfo = execution
                    )
                } else {
                    treeRoots.add(
                        TreeItem(
                            id = execution.id,
                            name = java.util.Date(execution.createdAtTimestampUtc).toString(),
                            itemInfo = execution
                        )
                    )
                }
            } else {
                var category = treeRoots.filter { it.name == categoryPath[0] }
                if (category.isEmpty()) {
                    treeRoots.add(
                        TreeItem(
                            id = categoryPath[0],
                            name = categoryPath[0],
                            children = mutableListOf()
                        )
                    )
                }
                category = treeRoots.filter { it.name == categoryPath[0] }
                categoryPath.removeAt(0)
                addItemToParent(category[0].children!!, execution, categoryPath, maxChildren)
            }
        }

        fun sortTree(
            treeRoots: MutableList<TreeItem<Execution>>,
            predicate: (first: TreeItem<Execution>, second: TreeItem<Execution>) -> Int
        ) {
            treeRoots.sortWith(Comparator(predicate))
            treeRoots.forEach { treeItem ->
                treeItem.children?.let {
                    sortTree(it, predicate)
                }
            }
        }

        fun getSubTree(
            treeRoots: MutableList<TreeItem<Execution>>,
            categoryPath: MutableList<String>
        ): List<TreeItem<Execution>> {
            return if (categoryPath.isEmpty()) {
                treeRoots
            } else {
                val category = treeRoots.filter { it.id == categoryPath[0] }
                if (category.isEmpty()) {
                    emptyList()
                } else {
                    categoryPath.removeAt(0)
                    getSubTree(category[0].children!!, categoryPath)
                }
            }
        }
    }

    data class TreeItem<T>(
        val id: String,
        val name: String,
        val itemInfo: T? = null,
        val children: MutableList<TreeItem<T>>? = null
    )

    data class Execution(
        val id: String,
        val nodeToOutputsMap: Map<String, Map<String, PortData>>? = null,
        val status: ExecutionStatus,
        val workflow_snapshot: ContinuumWorkflowModel? = null,
        val workflowId: String,
        val createdAtTimestampUtc: Long,
        val updatesAtTimestampUtc: Long
    )
}