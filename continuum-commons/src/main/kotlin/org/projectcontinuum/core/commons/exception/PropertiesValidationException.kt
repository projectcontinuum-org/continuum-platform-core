package org.projectcontinuum.core.commons.exception

import org.projectcontinuum.core.commons.utils.ValidationHelper.SchemaViolation

/**
 * Node properties do not satisfy the node's `propertiesSchema`. Thrown by
 * [org.projectcontinuum.core.commons.utils.ValidationHelper.validateJsonWithSchema]; [violations] is never empty.
 */
class PropertiesValidationException(
  val violations: List<SchemaViolation>
) : IllegalArgumentException("Properties do not match the schema: ${describe(violations)}") {

  companion object {
    /** One line per violation, joined with `; `, e.g. `/width: string found, integer expected`. */
    fun describe(violations: List<SchemaViolation>): String =
      violations.joinToString("; ") { "${it.path.ifEmpty { "/" }}: ${it.message}" }
  }
}
