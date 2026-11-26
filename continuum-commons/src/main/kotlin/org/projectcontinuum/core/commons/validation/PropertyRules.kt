package org.projectcontinuum.core.commons.validation

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.ValidationHelper

/**
 * A node's properties against its registered schema (never a client-sent one).
 *
 * - [ValidationMode.AUTHORING]: given properties are merged over the registered defaults; an undeclared key and a
 *   missing or empty required value each get one clear message, and the schema's own `additionalProperties` and
 *   `required` errors (and other errors on an empty required value, such as minLength) are not reported again.
 * - [ValidationMode.RUN]: the properties exactly as sent, every schema violation, the way the worker checks them.
 */
internal object PropertyRules {

  fun issues(
    key: String,
    given: Map<String, Any?>,
    descriptor: ContinuumWorkflowModel.NodeData,
    mode: ValidationMode
  ): List<WorkflowIssue> = when (mode) {
    ValidationMode.AUTHORING -> authoringIssues(key = key, given = given, descriptor = descriptor)
    ValidationMode.RUN -> runIssues(key = key, given = given, schema = descriptor.propertiesSchema)
  }

  private fun authoringIssues(key: String, given: Map<String, Any?>, descriptor: ContinuumWorkflowModel.NodeData): List<WorkflowIssue> {
    val schema = descriptor.propertiesSchema
    val declared = (schema["properties"] as? Map<*, *>)?.keys?.map { propertyName -> propertyName.toString() }.orEmpty()
    val unknownPropertyIssues = (given.keys - declared.toSet()).map { property ->
      WorkflowIssue(
        message = "Node '$key': unknown property '$property'. Known properties: ${declared.joinToString(", ").ifEmpty { "(none)" }}.",
        node = key,
        property = property
      )
    }
    val merged: Map<String, Any?> = descriptor.properties + given
    val required = (schema["required"] as? List<*>)?.map { propertyName -> propertyName.toString() }.orEmpty()
    val missingRequired = required.filter { property -> merged[property] == null || merged[property] == "" }
    val missingRequiredIssues = missingRequired.map { property ->
      WorkflowIssue(message = "Node '$key': required property '$property' has no value. Set it in properties.", node = key, property = property)
    }
    if (schema.isEmpty()) return unknownPropertyIssues + missingRequiredIssues
    val schemaIssues = schemaViolationIssues(key = key, properties = merged, schema = schema) { violation ->
      violation.keyword != "additionalProperties" && violation.keyword != "required" && violation.property !in missingRequired
    }
    return unknownPropertyIssues + missingRequiredIssues + schemaIssues
  }

  private fun runIssues(key: String, given: Map<String, Any?>, schema: Map<String, Any?>): List<WorkflowIssue> {
    if (schema.isEmpty()) return emptyList()
    return schemaViolationIssues(key = key, properties = given, schema = schema) { true }
  }

  private fun schemaViolationIssues(
    key: String,
    properties: Map<String, Any?>,
    schema: Map<String, Any?>,
    keep: (ValidationHelper.SchemaViolation) -> Boolean
  ): List<WorkflowIssue> =
    ValidationHelper.findSchemaViolations(properties, schema)
      .filter(keep)
      .map { violation ->
        WorkflowIssue(
          message = "Node '$key' properties: ${violation.path.ifEmpty { "/" }}: ${violation.message}",
          node = key,
          property = violation.property
        )
      }
}
