package org.projectcontinuum.core.api.server.utils

import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.commons.exception.PropertiesValidationException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.ValidationHelper

object WorkflowPropertiesValidator {

  /**
   * Checks every node's `properties` against its `propertiesSchema` and throws [InvalidRequestException] (HTTP 400
   * through `ApiExceptionHandler`) naming each invalid node and its violations. Call before anything reaches
   * Temporal or the database.
   */
  fun requireValidProperties(workflow: ContinuumWorkflowModel) {
    val problems = workflow.nodes.mapNotNull { node ->
      try {
        ValidationHelper.validateJsonWithSchema(node.data.properties, node.data.propertiesSchema)
        null
      } catch (e: PropertiesValidationException) {
        "node '${node.id}' (${node.data.nodeModel}): ${PropertiesValidationException.describe(e.violations)}"
      }
    }
    if (problems.isNotEmpty()) {
      throw InvalidRequestException("Invalid node properties: ${problems.joinToString(" | ")}")
    }
  }
}
