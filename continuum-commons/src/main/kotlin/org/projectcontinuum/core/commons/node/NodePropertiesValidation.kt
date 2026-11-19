package org.projectcontinuum.core.commons.node

import io.temporal.activity.Activity
import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.exception.PropertiesValidationException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.ValidationHelper

/**
 * Validates this node's `properties` against its `propertiesSchema` before it runs.
 *
 * On failure it throws a non-retriable [NodeRuntimeException] (cause: [PropertiesValidationException]). Retrying
 * would only fail again, so the worker records the node as failed instead of letting Temporal retry it.
 */
internal fun ContinuumWorkflowModel.Node.validatePropertiesOrFail() {
  try {
    ValidationHelper.validateJsonWithSchema(data.properties, data.propertiesSchema)
  } catch (e: PropertiesValidationException) {
    throw NodeRuntimeException(
      isRetriable = false,
      workflowId = currentWorkflowIdOrEmpty(),
      nodeId = id,
      message = "Invalid properties for node '$id' (${data.nodeModel}): ${PropertiesValidationException.describe(e.violations)}"
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
