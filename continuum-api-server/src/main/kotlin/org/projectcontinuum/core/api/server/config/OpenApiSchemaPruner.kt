package org.projectcontinuum.core.api.server.config

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI

/** Removes component schemas that no operation reaches, directly or through other schemas. */
object OpenApiSchemaPruner {

  private const val SCHEMA_REF_PREFIX = "#/components/schemas/"
  private val SCHEMA_REF = Regex("\"\\\$ref\"\\s*:\\s*\"${Regex.escape(SCHEMA_REF_PREFIX)}([^\"]+)\"")

  fun removeUnreferencedSchemas(openApi: OpenAPI) {
    val schemas = openApi.components?.schemas ?: return
    val mapper = Json.mapper()

    // Roots: everything except the schemas themselves (paths, and the other components such as responses).
    val rootsJson = mapper.writeValueAsString(openApi.paths) +
      mapper.writeValueAsString(openApi.components.copyWithoutSchemas())
    val reachable = mutableSetOf<String>()
    var newlyFound = referencedSchemaNames(rootsJson)
    while (newlyFound.isNotEmpty()) {
      reachable += newlyFound
      newlyFound = newlyFound
        .mapNotNull { schemaName -> schemas[schemaName] }
        .flatMap { schema -> referencedSchemaNames(mapper.writeValueAsString(schema)) }
        .filterNot { schemaName -> schemaName in reachable }
        .toSet()
    }
    schemas.keys.retainAll(reachable)
  }

  private fun referencedSchemaNames(json: String): Set<String> =
    SCHEMA_REF.findAll(json).map { match -> match.groupValues[1] }.toSet()

  private fun io.swagger.v3.oas.models.Components.copyWithoutSchemas() =
    io.swagger.v3.oas.models.Components()
      .responses(responses)
      .parameters(parameters)
      .requestBodies(requestBodies)
      .headers(headers)
      .examples(examples)
}
