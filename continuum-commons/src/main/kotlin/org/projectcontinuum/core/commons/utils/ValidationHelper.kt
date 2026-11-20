package org.projectcontinuum.core.commons.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaException
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SchemaRegistryConfig
import com.networknt.schema.SpecificationVersion
import org.projectcontinuum.core.commons.exception.PropertiesValidationException

class ValidationHelper {
  companion object {

    val mapper = ObjectMapper()

    /**
     * Throws [PropertiesValidationException] listing every violation when [properties] do not satisfy
     * [propertiesSchema]. An explicit check, not `assert`, so it runs whether or not the JVM has `-ea`.
     */
    fun validateJsonWithSchema(
      properties: Map<String, Any>,
      propertiesSchema: Map<String, Any>
    ) {
      val violations = findSchemaViolations(properties, propertiesSchema)
      if (violations.isNotEmpty()) throw PropertiesValidationException(violations)
    }

    /**
     * Validates [properties] against [propertiesSchema] and returns one [SchemaViolation] per problem, e.g. path
     * "/width", keyword "type", message "string found, integer expected". A violation on the object itself (e.g. a
     * missing required property) has an empty path.
     *
     * The dialect is draft 7 unless the schema declares another `$schema`, the same as the workbench: JSONForms
     * validates with Ajv 8, whose default dialect is draft 7, so a value accepted in the node dialog is accepted
     * here. `format` is checked, as `ajv-formats` does in the workbench.
     *
     * A schema that cannot be compiled (e.g. a dangling `$ref`, an invalid regex, a draft-4 boolean
     * `exclusiveMinimum`) is reported as a single violation with keyword [INVALID_SCHEMA_KEYWORD], not thrown, so
     * callers treat it as invalid input instead of an unexpected (retriable) error.
     */
    fun findSchemaViolations(
      properties: Map<String, Any?>,
      propertiesSchema: Map<String, Any?>
    ): List<SchemaViolation> {
      val schema = try {
        newRegistry().getSchema(mapper.writeValueAsString(propertiesSchema), InputFormat.JSON)
          .also { it.initializeValidators() }
      } catch (e: SchemaException) {
        return listOf(invalidSchema(e))
      }
      val errors = try {
        schema.validate(mapper.writeValueAsString(properties), InputFormat.JSON)
      } catch (e: SchemaException) {
        return listOf(invalidSchema(e))
      }
      return errors.map {
        val location = it.instanceLocation
        SchemaViolation(
          keyword = it.keyword,
          path = location.toString(),
          property = if (location.nameCount > 0) location.getName(0) else it.property,
          message = it.message
        )
      }
    }

    /** Keyword of the violation returned when `propertiesSchema` itself is not a valid JSON Schema. */
    const val INVALID_SCHEMA_KEYWORD = "\$schema"

    /**
     * A fresh registry per call. The registry caches every schema it loads, keyed by `$id`/`$ref` location. Node
     * schemas come from many workers and feature repos, so a shared registry would let one node's schema (for example
     * two nodes reusing the same `$id`) change how another node is validated.
     *
     * Callers pass a trusted schema: the one the worker registered (api-server), or the node class's own
     * `metadata.propertiesSchema` (worker). Never pass the `propertiesSchema` carried in a submitted workflow, which
     * is client-controlled.
     */
    private fun newRegistry(): SchemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7) {
      it.schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build())
    }

    private fun invalidSchema(e: SchemaException) = SchemaViolation(
      keyword = INVALID_SCHEMA_KEYWORD,
      path = "",
      property = null,
      message = "propertiesSchema is not a valid JSON Schema: ${e.message}"
    )
  }

  /**
   * One JSON Schema violation: the failing keyword (e.g. "type", "required"), the JSON Pointer of the failing value
   * (e.g. "/width"), the top-level property it belongs to, and the message.
   */
  data class SchemaViolation(val keyword: String?, val path: String, val property: String?, val message: String)
}
