package org.projectcontinuum.core.knime.scheduler.util

import java.util.UUID

/**
 * Single source of truth for the object-key layout so the upload, download, and delete
 * paths can never drift from the DB-recorded `object_key` value.
 */
object KnimeWorkflowKeyBuilder {

  private const val KNWF_EXTENSION = ".knwf"

  fun build(userId: String, workflowId: UUID): String =
    "knime-workflows/users/$userId/$workflowId$KNWF_EXTENSION"

  // Deliberately does not nest under build()'s key (".../$workflowId.knwf") — on MinIO's
  // filesystem/erasure-coded backend an object key can't also act as a directory prefix for
  // other objects ("parent is object" conflict), so execution keys use the bare workflow id.
  fun buildExecution(userId: String, workflowId: UUID, executionId: UUID): String =
    "knime-workflows-executions/users/$userId/$workflowId/executions/$executionId$KNWF_EXTENSION"

  fun hasKnwfExtension(fileName: String): Boolean =
    fileName.lowercase().endsWith(KNWF_EXTENSION)
}
