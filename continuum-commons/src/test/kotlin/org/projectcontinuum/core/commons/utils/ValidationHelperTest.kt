package org.projectcontinuum.core.commons.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.projectcontinuum.core.commons.exception.PropertiesValidationException
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader

class ValidationHelperTest {

  private val schema = mapOf(
    "type" to "object",
    "properties" to mapOf(
      "width" to mapOf("type" to "integer"),
      "mode" to mapOf("type" to "string", "enum" to listOf("A", "B"))
    ),
    "required" to listOf("width")
  )

  @Test
  fun `valid properties pass`() {
    assertDoesNotThrow { ValidationHelper.validateJsonWithSchema(mapOf("width" to 3, "mode" to "A"), schema) }
  }

  @Test
  fun `empty schema accepts anything`() {
    assertDoesNotThrow { ValidationHelper.validateJsonWithSchema(mapOf("x" to "y"), emptyMap()) }
  }

  @Test
  fun `invalid properties throw a typed exception carrying every violation`() {
    val e = assertThrows<PropertiesValidationException> {
      ValidationHelper.validateJsonWithSchema(mapOf("width" to "wide", "mode" to "C"), schema)
    }

    assertEquals(setOf("/width", "/mode"), e.violations.map { it.path }.toSet())
    assertEquals(setOf("type", "enum"), e.violations.map { it.keyword }.toSet())
    assertTrue(e.message!!.contains("/width"), e.message)
  }

  @Test
  fun `missing required property is a violation`() {
    val e = assertThrows<PropertiesValidationException> { ValidationHelper.validateJsonWithSchema(emptyMap(), schema) }

    assertEquals(listOf("required"), e.violations.map { it.keyword })
    assertEquals(listOf(""), e.violations.map { it.path }, "a violation on the object itself has an empty path")
  }

  /**
   * Gradle runs tests with `-ea`, which would hide an `assert`-based check. This loads commons and the Kotlin
   * stdlib again in a class loader whose assertions are off (the production default) and checks that invalid
   * properties still fail there.
   */
  @Test
  fun `validation fails with assertions disabled`() {
    val urls = System.getProperty("java.class.path").split(File.pathSeparator).map { File(it).toURI().toURL() }
    URLClassLoader(urls.toTypedArray(), ClassLoader.getPlatformClassLoader()).use { loader ->
      loader.setDefaultAssertionStatus(false)
      // Kotlin's assert() is gated on kotlin._Assertions; prove it is really off in this loader.
      assertFalse(loader.loadClass("kotlin._Assertions").desiredAssertionStatus())
      assertFalse(loader.loadClass(ValidationHelper::class.java.name).desiredAssertionStatus())

      val companion = loader.loadClass("${ValidationHelper::class.java.name}\$Companion")
      val instance = loader.loadClass(ValidationHelper::class.java.name).getField("Companion").get(null)
      val validate = companion.getMethod("validateJsonWithSchema", Map::class.java, Map::class.java)

      val thrown = assertThrows<InvocationTargetException> { validate.invoke(instance, mapOf("width" to "wide"), schema) }
      assertEquals(PropertiesValidationException::class.java.name, thrown.targetException.javaClass.name)
    }
  }

  // --- dialect: draft 7 by default, like the workbench (JSONForms + Ajv 8); the schema's own $schema otherwise ---

  private fun oneOfConst(dialect: String? = null) = buildMap<String, Any> {
    dialect?.let { put("\$schema", it) }
    put("type", "object")
    put(
      "properties",
      mapOf("backend" to mapOf("type" to "string", "oneOf" to listOf(mapOf("const" to "A"), mapOf("const" to "B"))))
    )
  }

  @Test
  fun `draft 7 is the default, so a oneOf of consts accepts exactly the listed values`() {
    assertDoesNotThrow { ValidationHelper.validateJsonWithSchema(mapOf("backend" to "A"), oneOfConst()) }
    val e = assertThrows<PropertiesValidationException> {
      ValidationHelper.validateJsonWithSchema(mapOf("backend" to "Z"), oneOfConst())
    }
    assertEquals(setOf("/backend"), e.violations.map { it.path }.toSet())
  }

  @Test
  fun `draft 7 keywords are enforced by default`() {
    val schema = mapOf(
      "type" to "object",
      "properties" to mapOf("n" to mapOf("type" to "number", "exclusiveMinimum" to 0)),
      "if" to mapOf("properties" to mapOf("mode" to mapOf("const" to "custom"))),
      "then" to mapOf("required" to listOf("url"))
    )

    assertDoesNotThrow { ValidationHelper.validateJsonWithSchema(mapOf("n" to 1, "mode" to "custom", "url" to "u"), schema) }
    val e = assertThrows<PropertiesValidationException> {
      ValidationHelper.validateJsonWithSchema(mapOf("n" to 0, "mode" to "custom"), schema)
    }
    assertEquals(setOf("exclusiveMinimum", "required"), e.violations.map { it.keyword }.toSet())
  }

  @Test
  fun `a schema declaring draft 4 is still validated as draft 4`() {
    // Draft 4 has no const, so each branch matches anything and oneOf fails: the declared dialect wins.
    val e = assertThrows<PropertiesValidationException> {
      ValidationHelper.validateJsonWithSchema(mapOf("backend" to "A"), oneOfConst("http://json-schema.org/draft-04/schema#"))
    }
    assertEquals(listOf("oneOf"), e.violations.map { it.keyword })
  }

  // --- format: asserted, as ajv-formats does in the workbench ---

  private val emailSchema = mapOf(
    "type" to "object",
    "properties" to mapOf(
      "email" to mapOf("type" to "string", "format" to "email"),
      "credential" to mapOf("type" to "string", "format" to "credential")
    )
  )

  @Test
  fun `known formats are checked`() {
    assertDoesNotThrow { ValidationHelper.validateJsonWithSchema(mapOf("email" to "a@b.co"), emailSchema) }
    val e = assertThrows<PropertiesValidationException> {
      ValidationHelper.validateJsonWithSchema(mapOf("email" to "not-an-email"), emailSchema)
    }
    assertEquals(listOf("format"), e.violations.map { it.keyword })
  }

  @Test
  fun `unknown formats such as the workbench's credential picker are ignored`() {
    assertDoesNotThrow { ValidationHelper.validateJsonWithSchema(mapOf("credential" to "my-cred"), emailSchema) }
  }

  // --- a schema that cannot be compiled is a violation, not an unexpected exception ---

  @Test
  fun `an invalid schema is reported as a violation`() {
    listOf(
      mapOf("type" to "object", "properties" to mapOf("n" to mapOf("type" to "number", "exclusiveMinimum" to true))),
      mapOf("type" to "object", "properties" to mapOf("n" to mapOf("\$ref" to "#/definitions/missing"))),
      mapOf("type" to "object", "properties" to mapOf("p" to mapOf("type" to "string", "pattern" to "([")))
    ).forEach { schema ->
      val e = assertThrows<PropertiesValidationException> {
        ValidationHelper.validateJsonWithSchema(mapOf("n" to 1, "p" to "x"), schema)
      }
      assertEquals(listOf(ValidationHelper.INVALID_SCHEMA_KEYWORD), e.violations.map { it.keyword }, schema.toString())
    }
  }
}
