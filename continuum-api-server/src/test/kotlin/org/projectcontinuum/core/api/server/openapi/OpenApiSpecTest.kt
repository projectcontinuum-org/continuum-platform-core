package org.projectcontinuum.core.api.server.openapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.File

/**
 * Guards the OpenAPI spec against drift: every endpoint is documented, internal endpoints stay hidden, every operation
 * is complete enough for client generation, and the spec equals the committed `docs/api/continuum-api-server.openapi.yaml`.
 *
 * After an intended API change, regenerate the committed spec and review the diff:
 * `./gradlew :continuum-api-server:test --tests '*OpenApiSpecTest*' -PupdateOpenApiSpec`
 */
@SpringBootTest(
  properties = [
    FullContextWithoutExternalSystems.MAIN_APPLICATION_YML,
    FullContextWithoutExternalSystems.H2_DATASOURCE,
    FullContextWithoutExternalSystems.H2_DRIVER,
    FullContextWithoutExternalSystems.H2_USER,
    FullContextWithoutExternalSystems.H2_PASSWORD,
    FullContextWithoutExternalSystems.H2_DIALECT,
    FullContextWithoutExternalSystems.NO_SCHEMA_INIT,
    FullContextWithoutExternalSystems.NO_MINIO_BUCKET_INIT,
    FullContextWithoutExternalSystems.TEMPORAL_TARGET,
    "continuum.core.api-docs.internal-group-enabled=true",
    // Relative: the committed spec works against any host, directly or behind a proxy prefix.
    "continuum.core.api-docs.server-url=/"
  ]
)
@AutoConfigureMockMvc
class OpenApiSpecTest : FullContextWithoutExternalSystems() {

  companion object {
    private const val CONTROLLER_PACKAGE = "org.projectcontinuum.core.api.server"
    private val INTERNAL_PATH_PREFIXES = listOf("/api/v1/admin")
    private val HTTP_METHODS = setOf("get", "put", "post", "delete", "patch", "head", "options", "trace")
  }

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private lateinit var handlerMapping: RequestMappingHandlerMapping

  private val objectMapper = ObjectMapper()

  private fun fetchJson(path: String): JsonNode {
    val body = mockMvc.perform(get(path))
      .andExpect(status().isOk)
      .andReturn().response.contentAsString
    System.getProperty("continuum.openapi.dump-dir")?.let { dumpDir ->
      File(dumpDir).mkdirs()
      File(dumpDir, path.trim('/').replace('/', '_') + ".json").writeText(body)
    }
    return objectMapper.readTree(body)
  }

  /** `METHOD path` of every operation in [spec]. */
  private fun operationKeys(spec: JsonNode): Set<String> =
    spec.path("paths").properties().flatMap { (path, pathItem) ->
      pathItem.properties()
        .filter { (method, _) -> method in HTTP_METHODS }
        .map { (method, _) -> "${method.uppercase()} $path" }
    }.toSet()

  private fun operations(spec: JsonNode): List<Pair<String, JsonNode>> =
    spec.path("paths").properties().flatMap { (path, pathItem) ->
      pathItem.properties()
        .filter { (method, _) -> method in HTTP_METHODS }
        .map { (method, operation) -> "${method.uppercase()} $path" to operation }
    }

  /** `METHOD path` of every handler of the API server's own `@RestController`s. */
  private fun restControllerOperationKeys(): Set<String> =
    handlerMapping.handlerMethods.entries
      .filter { (_, handler) ->
        handler.beanType.isAnnotationPresent(RestController::class.java) &&
          handler.beanType.packageName.startsWith(CONTROLLER_PACKAGE)
      }
      .flatMap { (mapping, _) ->
        val paths: Set<String> = mapping.pathPatternsCondition?.patternValues.orEmpty()
        val methods: List<String> = mapping.methodsCondition.methods.map { requestMethod -> requestMethod.name }
        paths.flatMap { path -> methods.map { method -> "$method $path" } }
      }
      .toSet()

  private fun isInternal(operationKey: String): Boolean {
    val path = operationKey.substringAfter(' ')
    return INTERNAL_PATH_PREFIXES.any { prefix -> path == prefix || path.startsWith("$prefix/") }
  }

  @Test
  fun `every request mapping is documented in the public or the internal group`() {
    val documented = operationKeys(fetchJson("/v3/api-docs/public")) + operationKeys(fetchJson("/v3/api-docs/internal"))
    val undocumented = restControllerOperationKeys() - documented
    assertTrue(undocumented.isEmpty(), "Request mappings missing from the spec: $undocumented")
  }

  @Test
  fun `internal endpoints are absent from the public and the ungrouped spec`() {
    for (specPath in listOf("/v3/api-docs/public", "/v3/api-docs")) {
      val spec = fetchJson(specPath)
      val leaked = operationKeys(spec).filter { isInternal(it) }
      assertTrue(leaked.isEmpty(), "Internal endpoints in $specPath: $leaked")
      // Neither may their schemas, tags or security schemes hint at them.
      val internalSchemas = spec.path("components").path("schemas").propertyNames().filter { it.startsWith("ScheduleValidation") }
      assertTrue(internalSchemas.isEmpty(), "Internal schemas in $specPath: $internalSchemas")
      val tagNames = spec.path("tags").map { tag -> tag.path("name").asString("") }
      assertFalse("Administration" in tagNames, "Administration tag in $specPath")
      assertTrue(spec.path("components").path("securitySchemes").path("continuumUserGroups").isMissingNode, "Groups scheme in $specPath")
    }
    val internalOnly = operationKeys(fetchJson("/v3/api-docs/internal"))
    assertTrue(internalOnly.isNotEmpty() && internalOnly.all { isInternal(it) }, "Internal group: $internalOnly")
  }

  @Test
  fun `no TODO reaches a published spec`() {
    for (specPath in listOf("/v3/api-docs/public", "/v3/api-docs/internal")) {
      val body = mockMvc.perform(get(specPath)).andExpect(status().isOk).andReturn().response.contentAsString
      assertFalse(body.contains("TODO"), "$specPath contains a TODO; open questions belong in docs/api/ENDPOINT_INVENTORY.md")
    }
  }

  @Test
  fun `every operation has a summary, a tag, an operationId and an error response`() {
    for (specPath in listOf("/v3/api-docs/public", "/v3/api-docs/internal")) {
      for ((operationKey, operation) in operations(fetchJson(specPath))) {
        assertFalse(operation.path("summary").asString("").isBlank(), "$operationKey has no summary")
        assertTrue(operation.path("summary").asString("").length < 80, "$operationKey summary is 80 characters or longer")
        assertTrue(operation.path("tags").size() > 0, "$operationKey has no tag")
        assertFalse(operation.path("operationId").asString("").isBlank(), "$operationKey has no operationId")
        val errorStatuses = operation.path("responses").propertyNames().filter { it.startsWith("4") || it.startsWith("5") }
        assertTrue(errorStatuses.isNotEmpty(), "$operationKey documents no error response")
      }
    }
  }

  @Test
  fun `operationIds are unique across all groups`() {
    val operationIds = listOf("/v3/api-docs/public", "/v3/api-docs/internal")
      .flatMap { specPath -> operations(fetchJson(specPath)).map { (_, operation) -> operation.path("operationId").asString("") } }
    val duplicates = operationIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    assertTrue(duplicates.isEmpty(), "Duplicate operationIds: $duplicates")
  }

  @Test
  fun `identity headers are a security scheme, never a parameter`() {
    val spec = fetchJson("/v3/api-docs/public")
    val identityParameters = operations(spec).flatMap { (operationKey, operation) ->
      operation.path("parameters")
        .filter { parameter -> parameter.path("name").asString("").startsWith("x-continuum-") }
        .map { "$operationKey ${it.path("name").asString("")}" }
    }
    assertTrue(identityParameters.isEmpty(), "Identity headers documented as parameters: $identityParameters")
    assertEquals("x-continuum-user-id", spec.path("components").path("securitySchemes").path("continuumUserId").path("name").asString(""))
  }

  @Test
  fun `the spec has real metadata`() {
    val info = fetchJson("/v3/api-docs/public").path("info")
    assertNotEquals("OpenAPI definition", info.path("title").asString(""))
    assertFalse(info.path("description").asString("").isBlank())
    assertFalse(info.path("license").path("name").asString("").isBlank())
  }

  @Test
  fun `the public spec equals the committed docs-api spec`() {
    val generated = mockMvc.perform(get("/v3/api-docs.yaml/public"))
      .andExpect(status().isOk)
      .andReturn().response.getContentAsString(Charsets.UTF_8)
      .let { if (it.endsWith("\n")) it else "$it\n" }
    val committedSpecFile = File(System.getProperty("continuum.openapi.spec-file") ?: error("continuum.openapi.spec-file is not set (run through Gradle)"))
    if (System.getProperty("continuum.openapi.update-spec").toBoolean()) {
      committedSpecFile.parentFile.mkdirs()
      committedSpecFile.writeText(generated)
      return
    }
    assertTrue(committedSpecFile.exists(), "${committedSpecFile.path} is missing; generate it with -PupdateOpenApiSpec")
    assertEquals(
      committedSpecFile.readText(),
      generated,
      "The API changed but ${committedSpecFile.name} was not regenerated. Run " +
        "./gradlew :continuum-api-server:test --tests '*OpenApiSpecTest*' -PupdateOpenApiSpec and review the diff."
    )
  }
}
