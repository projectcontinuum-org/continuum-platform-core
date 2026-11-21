package org.projectcontinuum.core.cluster.manager.openapi

import io.fabric8.kubernetes.client.KubernetesClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.File

/**
 * Guards the OpenAPI spec against drift: every endpoint is documented, `/authorize` stays hidden, every operation is
 * complete enough for client generation, and the spec equals the committed `docs/api/continuum-cluster-manager.openapi.yaml`.
 *
 * After an intended API change, regenerate the committed spec and review the diff:
 * `./gradlew :continuum-cluster-manager:test --tests '*OpenApiSpecTest*' -PupdateOpenApiSpec`
 */
@SpringBootTest(
  properties = [
    OpenApiSpecTest.MAIN_APPLICATION_YML,
    // The main file's database settings point at PostgreSQL; keep the H2 ones of the test application.yml.
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.schema-locations=classpath:schema-h2.sql",
    "continuum.core.api-docs.internal-group-enabled=true",
    // Relative: the committed spec works against any host, directly or behind a proxy prefix.
    "continuum.core.api-docs.server-url=/"
  ]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSpecTest {

  companion object {
    /** `src/test/resources/application.yml` shadows the main one, so the real springdoc/server settings are imported. */
    const val MAIN_APPLICATION_YML = "spring.config.import=file:src/main/resources/application.yml"
    private const val CONTROLLER_PACKAGE = "org.projectcontinuum.core.cluster.manager"
    private val INTERNAL_PATHS = setOf("/api/v1/workbench/authorize")
    private val HTTP_METHODS = setOf("get", "put", "post", "delete", "patch", "head", "options", "trace")
  }

  @MockitoBean
  lateinit var kubernetesClient: KubernetesClient

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

  private fun operations(spec: JsonNode): List<Pair<String, JsonNode>> =
    spec.path("paths").properties().flatMap { (path, pathItem) ->
      pathItem.properties()
        .filter { (method, _) -> method in HTTP_METHODS }
        .map { (method, operation) -> "${method.uppercase()} $path" to operation }
    }

  private fun operationKeys(spec: JsonNode): Set<String> = operations(spec).map { (operationKey, _) -> operationKey }.toSet()

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

  private fun isInternal(operationKey: String): Boolean = operationKey.substringAfter(' ') in INTERNAL_PATHS

  @Test
  fun `every request mapping is documented in the public or the internal group`() {
    val documented = operationKeys(fetchJson("/v3/api-docs/public")) + operationKeys(fetchJson("/v3/api-docs/internal"))
    val undocumented = restControllerOperationKeys() - documented
    assertTrue(undocumented.isEmpty(), "Request mappings missing from the spec: $undocumented")
  }

  @Test
  fun `the ForwardAuth endpoint is absent from the public and the ungrouped spec`() {
    for (specPath in listOf("/v3/api-docs/public", "/v3/api-docs")) {
      val leaked = operationKeys(fetchJson(specPath)).filter { isInternal(it) }
      assertTrue(leaked.isEmpty(), "Internal endpoints in $specPath: $leaked")
    }
    val internalOnly = operationKeys(fetchJson("/v3/api-docs/internal"))
    assertTrue(internalOnly.isNotEmpty() && internalOnly.all { isInternal(it) }, "Internal group: $internalOnly")
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
  fun `the identity header is a security scheme, not a parameter of the public operations`() {
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
        "./gradlew :continuum-cluster-manager:test --tests '*OpenApiSpecTest*' -PupdateOpenApiSpec and review the diff."
    )
  }
}
