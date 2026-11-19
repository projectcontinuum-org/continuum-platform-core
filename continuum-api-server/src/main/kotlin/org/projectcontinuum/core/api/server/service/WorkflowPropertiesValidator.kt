package org.projectcontinuum.core.api.server.service

import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.commons.exception.PropertiesValidationException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.ValidationHelper
import org.springframework.stereotype.Component

/**
 * Checks a submitted workflow's node properties against the node registry before it is stored or started.
 *
 * The schema always comes from the registry (the manifest the worker registered for `data.nodeModel`), never from the
 * request: the `propertiesSchema` in the request body is client-controlled, so trusting it would let a client send
 * `{}` and skip validation. Registry reads are cached at the repository (`NodeTreeEntryRepository.findByNodeId`).
 */
@Component
class WorkflowPropertiesValidator(
  private val nodeExplorerService: NodeExplorerService
) {

  /**
   * Throws [InvalidRequestException] (HTTP 400 through `ApiExceptionHandler`) naming every node that is not
   * registered or whose properties violate its registered schema. Call before anything reaches Temporal or the
   * database.
   */
  fun requireValidProperties(workflow: ContinuumWorkflowModel) {
    // One lookup per node type per request, even when the cache is cold or disabled.
    val schemas = workflow.nodes.map { it.data.nodeModel }.distinct()
      .associateWith { nodeExplorerService.getNodeMetadata(it)?.propertiesSchema }

    val problems = workflow.nodes.mapNotNull { node ->
      val label = "node '${node.id}' (${node.data.nodeModel})"
      val schema = schemas[node.data.nodeModel]
        ?: return@mapNotNull "$label: no registered node with this nodeModel"
      try {
        ValidationHelper.validateJsonWithSchema(node.data.properties, schema)
        null
      } catch (e: PropertiesValidationException) {
        "$label: ${PropertiesValidationException.describe(e.violations)}"
      }
    }
    if (problems.isNotEmpty()) {
      throw InvalidRequestException("Invalid node properties: ${problems.joinToString(" | ")}")
    }
  }
}
