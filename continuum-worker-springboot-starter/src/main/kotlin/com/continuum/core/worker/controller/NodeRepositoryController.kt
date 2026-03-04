package com.continuum.core.worker.controller

import com.continuum.core.commons.model.NodeRepoTreeItem
import com.continuum.core.commons.node.ContinuumNodeModel
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller that exposes the node repository API.
 *
 * This controller provides an endpoint to retrieve all available Continuum nodes
 * organized in a hierarchical tree structure based on their categories.
 * The node repository is populated at startup by scanning all registered
 * [ContinuumNodeModel] beans in the Spring application context.
 *
 * ## API Endpoints
 * - `GET /api/v1/node-repo` - Returns the complete node repository tree
 *
 * ## Node Organization
 * Nodes are organized into a tree structure where:
 * - Internal nodes represent categories (e.g., "IO", "Transform", "ML")
 * - Leaf nodes represent actual Continuum nodes with their metadata
 *
 * @property nodeModels Provider for all registered [ContinuumNodeModel] beans
 * @author Continuum Team
 * @since 1.0.0
 * @see ContinuumNodeModel
 * @see NodeRepoTreeItem
 */
@RestController
@RequestMapping("/api/v1/node-repo")
class NodeRepositoryController(
  private val nodeModels: ObjectProvider<ContinuumNodeModel>
) {

  /** Root nodes of the repository tree structure */
  private val treeRoots = mutableListOf<NodeRepoTreeItem>()

  /**
   * Initializes the node repository tree after bean construction.
   *
   * This method iterates through all registered [ContinuumNodeModel] beans,
   * extracts their categories, and builds a hierarchical tree structure.
   * Each node can belong to multiple categories (specified as "/" separated paths).
   *
   * For example, a node with category "IO/File" will be placed under:
   * ```
   * IO
   * └── File
   *     └── NodeModel
   * ```
   */
  @PostConstruct
  fun loadNodes() {
    // Iterate through all registered node models
    nodeModels.forEach {
      // Each node can have multiple categories
      it.categories.forEach { category ->
        // Split the category path (e.g., "IO/File" -> ["IO", "File"])
        val categoryPath = category.split("/")
        // Recursively add the node to the appropriate category in the tree
        addNodeModelToCategories(treeRoots, it, categoryPath.toMutableList())
      }
    }
  }

  /**
   * Retrieves the complete node repository tree.
   *
   * @return List of root [NodeRepoTreeItem] nodes representing the repository structure
   */
  @GetMapping
  fun getNodes(): List<NodeRepoTreeItem> {
    return treeRoots
  }

  /**
   * Recursively adds a node model to the appropriate position in the category tree.
   *
   * This method traverses or creates the category hierarchy and places the node
   * at the correct leaf position. If intermediate category nodes don't exist,
   * they are created automatically.
   *
   * @param treeRoots The current level of tree nodes to search/modify
   * @param nodeModel The node model to add to the tree
   * @param categoryPath The remaining category path segments to traverse
   */
  private fun addNodeModelToCategories(
    treeRoots: MutableList<NodeRepoTreeItem>,
    nodeModel: ContinuumNodeModel,
    categoryPath: MutableList<String>
  ) {
    if (categoryPath.isEmpty()) {
      // Base case: We've reached the target category, add the node as a leaf
      treeRoots.add(
        NodeRepoTreeItem(
          id = nodeModel.javaClass.name,
          name = nodeModel.javaClass.simpleName,
          nodeInfo = nodeModel.metadata
        )
      )
    } else {
      // Recursive case: Navigate to or create the next category level
      var category = treeRoots.filter { it.name == categoryPath[0] }

      // Create the category node if it doesn't exist
      if (category.isEmpty()) {
        treeRoots.add(
          NodeRepoTreeItem(
            id = categoryPath[0],
            name = categoryPath[0],
            children = mutableListOf()
          )
        )
      }

      // Get the (now existing) category node
      category = treeRoots.filter { it.name == categoryPath[0] }

      // Remove the current category from the path and recurse
      categoryPath.removeAt(0)
      addNodeModelToCategories(category[0].children!!, nodeModel, categoryPath)
    }
  }
}