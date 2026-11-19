package org.projectcontinuum.core.commons.node

import io.temporal.activity.Activity
import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.exception.PropertiesValidationException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.ValidationHelper

/**
 * Validates [node]'s `properties` against this node class's own schema ([ContinuumNodeModel.metadata]) before it
 * runs. The `propertiesSchema` carried in the workflow is client-supplied and is never used: trusting it would let a
 * client send `{}` and skip validation.
 *
 * On failure it throws a non-retriable [NodeRuntimeException] (cause: [PropertiesValidationException]). Retrying
 * would only fail again, so the worker records the node as failed instead of letting Temporal retry it.
 */
internal fun ContinuumNodeModel.validatePropertiesOrFail(node: ContinuumWorkflowModel.Node) {
  try {
    ValidationHelper.validateJsonWithSchema(node.data.properties, metadata.propertiesSchema)
  } catch (e: PropertiesValidationException) {
    throw NodeRuntimeException(
      isRetriable = false,
      workflowId = currentWorkflowIdOrEmpty(),
      nodeId = node.id,
      message = "Invalid properties for node '${node.id}' (${node.data.nodeModel}): ${PropertiesValidationException.describe(e.violations)}"
    ).apply { initCause(e) }
  }
}

/** The Temporal workflow id when called from an activity; empty outside one (e.g. in unit tests). */
private fun currentWorkflowIdOrEmpty(): String =
  try {
    Activity.getExecutionContext().info.workflowId
  } catch (_: IllegalStateException) {
    ""
  }
