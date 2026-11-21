package org.projectcontinuum.core.api.server.utils

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.ExecutionStatus
import org.projectcontinuum.core.commons.model.PortData
import java.util.Date
import io.swagger.v3.oas.annotations.media.Schema

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
            name = Date(execution.createdAtTimestampUtc).toString(),
            itemInfo = execution
          )
        } else {
          treeRoots.add(
            TreeItem(
              id = execution.id,
              name = Date(execution.createdAtTimestampUtc).toString(),
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

  /** Only used as `TreeItem<Execution>`, documented as [WorkflowTreeItem]. */
  data class TreeItem<T>(
    val id: String,
    val name: String,
    val itemInfo: T? = null,
    val children: MutableList<TreeItem<T>>? = null
  )

  @Schema(name = "WorkflowTreeExecution", description = "One execution in the workflow tree, rebuilt from its Temporal history.")
  data class Execution(
    @field:Schema(description = "Workflow id (same as `workflowId`).", example = "6b1f0e4e-8f4a-4d8c-9d61-3c2b7f1a9e10")
    val id: String,
    @field:Schema(
      description = "Outputs per node: node id → port id → output. From the result of a completed execution, otherwise " +
        "from the node activities completed so far.",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED,
      nullable = true
    )
    val nodeToOutputsMap: Map<String, Map<String, PortData>>? = null,
    @field:Schema(description = "Last workflow execution event of the history, or `UNKNOWN`.", example = "WORKFLOW_EXECUTION_COMPLETED")
    val status: ExecutionStatus,
    @field:Schema(
      description = "The workflow as submitted (the Temporal input). Legacy snake_case name, kept for compatibility; " +
        "new clients should read `data.workflowSnapshot` of `GET /api/v1/workflow-runs/{workflowId}` instead.",
      requiredMode = Schema.RequiredMode.NOT_REQUIRED,
      nullable = true,
      deprecated = true
    )
    val workflow_snapshot: ContinuumWorkflowModel? = null,
    @field:Schema(description = "Workflow id.", example = "6b1f0e4e-8f4a-4d8c-9d61-3c2b7f1a9e10")
    val workflowId: String,
    @field:Schema(description = "When the first workflow task was scheduled, in epoch milliseconds (UTC).", example = "1763716502481")
    val createdAtTimestampUtc: Long,
    @field:Schema(description = "Time of the last history event, in epoch milliseconds (UTC).", example = "1763716509112")
    val updatesAtTimestampUtc: Long
  )

  /** Documentation type of `TreeItem<Execution>` (a recursive generic), so it gets one precise, unique schema. */
  @Schema(name = "WorkflowTreeItem", description = "A folder of the workflow path, or one execution of a workflow.")
  abstract class WorkflowTreeItem {
    @get:Schema(description = "Folder name, or the workflow id for an execution.", example = "examples")
    abstract val id: String
    @get:Schema(description = "Folder name, or the execution's start time (`java.util.Date#toString`) for an execution.", example = "examples")
    abstract val name: String
    @get:Schema(description = "The execution; null for folders.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    abstract val itemInfo: Execution?
    @get:Schema(description = "Sub-folders and executions, newest first; null for executions.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    abstract val children: List<WorkflowTreeItem>?
  }
}