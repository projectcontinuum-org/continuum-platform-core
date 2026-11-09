package org.projectcontinuum.core.commons.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion

class ValidationHelper {
  companion object {

    val mapper = ObjectMapper()

    fun validateJsonWithSchema(
      properties: Map<String, Any>,
      propertiesSchema: Map<String, Any>
    ) {
      val errorMessages = findSchemaViolations(properties, propertiesSchema)
      assert(errorMessages.isEmpty()) { "Validation failed: $errorMessages" }
    }

    /**
     * Validates [properties] against [propertiesSchema] (JSON Schema, draft 4 unless the schema declares another
     * dialect) and returns one message per violation, e.g. "$.width: string found, integer expected".
     */
    fun findSchemaViolations(
      properties: Map<String, Any?>,
      propertiesSchema: Map<String, Any?>
    ): List<SchemaViolation> {
      val registry = SchemaRegistry.withDefaultDialect(
        SpecificationVersion.DRAFT_4
      )
      val schema = registry.getSchema(
        mapper.writeValueAsString(propertiesSchema),
        InputFormat.JSON
      )
      return schema.validate(
        mapper.writeValueAsString(properties),
        InputFormat.JSON
      ).map {
        val location = it.instanceLocation
        SchemaViolation(
          keyword = it.keyword,
          path = location.toString(),
          property = if (location.nameCount > 0) location.getName(0) else it.property,
          message = it.message
        )
      }
    }
  }

  /**
   * One JSON Schema violation: the failing keyword (e.g. "type", "required"), the JSON Pointer of the failing value
   * (e.g. "/width"), the top-level property it belongs to, and the message.
   */
  data class SchemaViolation(val keyword: String?, val path: String, val property: String?, val message: String)
}
