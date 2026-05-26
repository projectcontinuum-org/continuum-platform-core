package org.projectcontinuum.core.credentials.entity

/**
 * Wrapper type for JSONB column values. This must be a regular class (not an
 * inline value class) so that Spring Data JDBC sees it as a distinct type at
 * runtime and applies the registered PGobject converters.
 */
data class JsonValue(val value: String) {
  override fun toString(): String = value
}
