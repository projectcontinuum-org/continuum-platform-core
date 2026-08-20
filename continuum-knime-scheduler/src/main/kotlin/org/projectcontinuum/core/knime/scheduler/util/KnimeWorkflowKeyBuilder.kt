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

  fun hasKnwfExtension(fileName: String): Boolean =
    fileName.lowercase().endsWith(KNWF_EXTENSION)
}
