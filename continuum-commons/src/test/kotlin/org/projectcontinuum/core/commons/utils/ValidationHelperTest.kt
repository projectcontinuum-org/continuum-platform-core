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

  // --- dialect: draft 4 by default, the schema's own $schema otherwise (unchanged behaviour) ---

  private fun oneOfConst(dialect: String? = null) = buildMap<String, Any> {
    dialect?.let { put("\$schema", it) }
    put("type", "object")
    put(
      "properties",
      mapOf("backend" to mapOf("type" to "string", "oneOf" to listOf(mapOf("const" to "A"), mapOf("const" to "B"))))
    )
  }

  @Test
  fun `draft 4 is the default and does not know const, so a oneOf of consts never matches exactly one`() {
    // Nodes that use oneOf/const without declaring a newer $schema fail for every value under the default dialect.
    val e = assertThrows<PropertiesValidationException> {
      ValidationHelper.validateJsonWithSchema(mapOf("backend" to "A"), oneOfConst())
    }
    assertEquals(listOf("oneOf"), e.violations.map { it.keyword })
  }

  @Test
  fun `a schema declaring draft 7 is validated as draft 7`() {
    val draft7 = oneOfConst("http://json-schema.org/draft-07/schema#")

    assertDoesNotThrow { ValidationHelper.validateJsonWithSchema(mapOf("backend" to "A"), draft7) }
    assertThrows<PropertiesValidationException> { ValidationHelper.validateJsonWithSchema(mapOf("backend" to "Z"), draft7) }
  }
}
