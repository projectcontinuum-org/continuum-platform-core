package org.projectcontinuum.core.api.server.service

import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.commons.exception.PropertiesValidationException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.ValidationHelper
import org.springframework.stereotype.Component

/**
 * Checks a workflow's node properties against the node registry.
 *
 * The schema always comes from the registry (the manifest the worker registered for `data.nodeModel`), never from the
 * workflow: the `propertiesSchema` in a request body is client-controlled, so trusting it would let a client send `{}`
 * and skip validation. Registry reads are cached at the repository (`NodeTreeEntryRepository.findByNodeId`).
 */
@Component
class WorkflowPropertiesValidator(
  private val nodeExplorerService: NodeExplorerService
) {

  /** One node that fails the check. */
  data class NodeProblem(val nodeId: String, val nodeModel: String, val kind: Kind, val detail: String) {
    enum class Kind {
      /** Properties violate the registered schema. */
      INVALID_PROPERTIES,

      /** No node with this `nodeModel` is registered, so its properties cannot be checked. */
      UNREGISTERED_NODE
    }

    override fun toString() = "node '$nodeId' ($nodeModel): $detail"
  }

  /** Every node of [workflow] that is unregistered or whose properties violate its registered schema. */
  fun findProblems(workflow: ContinuumWorkflowModel): List<NodeProblem> {
    // One lookup per node type, even when the cache is cold or disabled.
    val schemas = workflow.nodes.map { it.data.nodeModel }.distinct()
      .associateWith { nodeExplorerService.getNodeMetadata(it)?.propertiesSchema }

    return workflow.nodes.mapNotNull { node ->
      val nodeModel = node.data.nodeModel
      val schema = schemas[nodeModel]
        ?: return@mapNotNull NodeProblem(node.id, nodeModel, NodeProblem.Kind.UNREGISTERED_NODE, "no registered node with this nodeModel")
      try {
        ValidationHelper.validateJsonWithSchema(node.data.properties, schema)
        null
      } catch (e: PropertiesValidationException) {
        NodeProblem(node.id, nodeModel, NodeProblem.Kind.INVALID_PROPERTIES, PropertiesValidationException.describe(e.violations))
      }
    }
  }

  /**
   * Throws [InvalidRequestException] (HTTP 400 through `ApiExceptionHandler`) naming every problem [findProblems]
   * reports. Call before anything reaches Temporal or the database.
   */
  fun requireValidProperties(workflow: ContinuumWorkflowModel) {
    val problems = findProblems(workflow)
    if (problems.isNotEmpty()) {
      throw InvalidRequestException("Invalid node properties: ${problems.joinToString(" | ")}")
    }
  }
}
