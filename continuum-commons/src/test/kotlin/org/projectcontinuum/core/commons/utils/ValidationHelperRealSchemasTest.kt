package org.projectcontinuum.core.commons.utils

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.projectcontinuum.core.commons.exception.PropertiesValidationException
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.projectcontinuum.core.commons.utils.ValidationHelper.SchemaViolation

/**
 * [ValidationHelper] against schema shapes taken from real feature nodes (as registered, i.e. parsed from the node
 * manifest JSON), so enum handling, nested objects and the dialect rules are pinned down, not just the mechanism.
 *
 * The workbench validates the same schemas with JSONForms / Ajv 8 (draft-07 build). Where the two must agree it is
 * stated per test; see [Dialects] for where they do not.
 */
class ValidationHelperRealSchemasTest {

  private val mapper = ObjectMapper()

  private fun schema(json: String): Map<String, Any> = mapper.readValue(json, object : TypeReference<Map<String, Any>>() {})

  private fun violations(properties: Map<String, Any?>, schema: Map<String, Any>): List<SchemaViolation> =
    ValidationHelper.findSchemaViolations(properties, schema)

  private fun assertValid(properties: Map<String, Any?>, schema: Map<String, Any>) {
    val found = violations(properties, schema)
    assertTrue(found.isEmpty(), "expected valid, got $found")
  }

  /** Asserts exactly the given (keyword, path) pairs, in any order. */
  private fun assertViolations(properties: Map<String, Any?>, schema: Map<String, Any>, vararg expected: Pair<String, String>) {
    assertEquals(expected.toSet(), violations(properties, schema).map { it.keyword to it.path }.toSet())
  }

  // --- enum: RestNodeModel (continuum-feature-base) ---

  @Nested
  inner class Enums {

    /** `RestNodeModel`: two string enums (one with the JSONForms-only `enumNames`), two required fields. */
    private val rest = schema(
      """
      {
        "type": "object",
        "properties": {
          "method":   { "type": "string", "enum": ["GET", "POST", "PUT", "DELETE"], "default": "GET" },
          "url":      { "type": "string" },
          "payload":  { "type": "string" },
          "authType": { "type": "string", "enum": ["None", "Basic", "Token"],
                        "enumNames": ["None", "Basic Authentication", "Bearer Token"], "default": "None" },
          "credential": { "type": "string" }
        },
        "required": ["method", "url"]
      }
      """
    )

    /** `FingerprintNodeModel` (cheminformatics): enum next to a bounded integer. */
    private val fingerprint = schema(
      """
      {
        "type": "object",
        "properties": {
          "smilesColumn":    { "type": "string" },
          "fingerprintType": { "type": "string",
                               "enum": ["Morgan", "FeatMorgan", "AtomPair", "Torsion", "RDKit", "Avalon", "Layered", "MACCS", "Pattern"],
                               "default": "Morgan" },
          "numBits": { "type": "integer", "default": 2048, "minimum": 64, "maximum": 16384 },
          "radius":  { "type": "integer", "default": 2, "minimum": 1, "maximum": 10 }
        }
      }
      """
    )

    @Test
    fun `the node's registered defaults are valid`() {
      assertValid(
        mapOf("method" to "GET", "url" to "https://api.example.com/data?id=\${row.id}", "payload" to "", "authType" to "None", "credential" to ""),
        rest
      )
      assertValid(mapOf("smilesColumn" to "smiles", "fingerprintType" to "Morgan", "numBits" to 2048, "radius" to 2), fingerprint)
    }

    @Test
    fun `every listed value is accepted`() {
      listOf("GET", "POST", "PUT", "DELETE").forEach { assertValid(mapOf("method" to it, "url" to "u"), rest) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["get", "PATCH", " GET", "GET ", ""])
    fun `enum matching is exact - case, whitespace and empty string are rejected`(method: String) {
      assertViolations(mapOf("method" to method, "url" to "u"), rest, "enum" to "/method")
    }

    @Test
    fun `enumNames is a display label only - a label is not an accepted value`() {
      assertViolations(mapOf("method" to "GET", "url" to "u", "authType" to "Bearer Token"), rest, "enum" to "/authType")
    }

    @Test
    fun `a value of the wrong type fails both type and enum`() {
      assertViolations(mapOf("method" to 1, "url" to "u"), rest, "type" to "/method", "enum" to "/method")
    }

    @Test
    fun `an absent optional enum is fine, an absent required field is not`() {
      assertValid(mapOf("method" to "GET", "url" to "u"), rest)
      assertViolations(mapOf("url" to "u"), rest, "required" to "")
    }

    @Test
    fun `enum and bounds are reported together, each at its own path`() {
      assertViolations(
        mapOf("fingerprintType" to "ECFP4", "numBits" to 32, "radius" to 11),
        fingerprint,
        "enum" to "/fingerprintType", "minimum" to "/numBits", "maximum" to "/radius"
      )
    }

    @Test
    fun `an integer field accepts a whole-number float, like Ajv, but not a fraction`() {
      assertValid(mapOf("numBits" to 2048.0), fingerprint)
      assertViolations(mapOf("numBits" to 2048.5), fingerprint, "type" to "/numBits")
    }

    /** `DescriptorCalculationNodeModel` (cheminformatics): an array whose items are an enum, unique. */
    private val descriptors = schema(
      """
      {
        "type": "object",
        "properties": {
          "smilesColumn": { "type": "string" },
          "descriptors": { "type": "array", "uniqueItems": true,
                           "items": { "type": "string", "enum": ["AMW", "ExactMW", "SlogP", "TPSA", "NumHBD", "NumHBA", "NumRotatableBonds", "NumRings"] } }
        },
        "required": ["smilesColumn", "descriptors"]
      }
      """
    )

    @Test
    fun `an array of enum values is checked item by item`() {
      assertValid(mapOf("smilesColumn" to "smiles", "descriptors" to listOf("AMW", "SlogP", "TPSA")), descriptors)
      assertViolations(
        mapOf("smilesColumn" to "smiles", "descriptors" to listOf("AMW", "LogP", "TPSA", "MolWt")),
        descriptors,
        "enum" to "/descriptors/1", "enum" to "/descriptors/3"
      )
    }

    @Test
    fun `duplicate enum values in a uniqueItems array are rejected`() {
      assertViolations(mapOf("smilesColumn" to "smiles", "descriptors" to listOf("AMW", "AMW")), descriptors, "uniqueItems" to "/descriptors")
    }

    @Test
    fun `an empty array is valid - required only means present`() {
      assertValid(mapOf("smilesColumn" to "smiles", "descriptors" to emptyList<String>()), descriptors)
    }
  }

  // --- nested objects: KNIMEWorkflowExecutorNodeModel (continuum-feature-knime) ---

  @Nested
  inner class NestedObjects {

    /** `KNIMEWorkflowExecutorNodeModel`: arrays of objects, each with its own required fields and an inner enum. */
    private val knime = schema(
      """
      {
        "type": "object",
        "properties": {
          "workflowLocation":   { "type": "string" },
          "executionUploadUrl": { "type": "string" },
          "timeoutSeconds": { "type": "integer", "default": 300, "minimum": 10 },
          "resetWorkflow":  { "type": "boolean", "default": false },
          "workflowVariables": {
            "type": "array", "default": [],
            "items": {
              "type": "object",
              "properties": {
                "name":  { "type": "string" },
                "value": { "type": "string" },
                "type":  { "type": "string", "enum": ["String", "int", "double"], "default": "String" }
              },
              "required": ["name", "value", "type"]
            }
          },
          "workflowCredentials": {
            "type": "array", "default": [],
            "items": {
              "type": "object",
              "properties": { "name": { "type": "string" }, "credential": { "type": "string" } },
              "required": ["name", "credential"]
            }
          }
        },
        "required": ["workflowLocation", "executionUploadUrl"]
      }
      """
    )

    private fun props(vararg extra: Pair<String, Any?>) =
      mapOf<String, Any?>("workflowLocation" to "s3://bucket/wf.knwf", "executionUploadUrl" to "https://x/upload") + extra

    @Test
    fun `the node's registered defaults are valid`() {
      assertValid(props("timeoutSeconds" to 300, "resetWorkflow" to false, "workflowVariables" to emptyList<Any>(), "workflowCredentials" to emptyList<Any>()), knime)
    }

    @Test
    fun `well-formed nested entries are valid`() {
      assertValid(
        props(
          "workflowVariables" to listOf(mapOf("name" to "threshold", "value" to "0.5", "type" to "double")),
          "workflowCredentials" to listOf(mapOf("name" to "db", "credential" to "prod-db"))
        ),
        knime
      )
    }

    @Test
    fun `a nested missing required field is reported at the item, with the top-level property`() {
      val found = violations(props("workflowVariables" to listOf(mapOf("name" to "a", "value" to "1", "type" to "int"), mapOf("name" to "b"))), knime)

      // One violation per missing field, all at the item's path.
      assertEquals(listOf("required" to "/workflowVariables/1", "required" to "/workflowVariables/1"), found.map { it.keyword to it.path })
      assertTrue(found.any { "value" in it.message } && found.any { "type" in it.message }, found.toString())
      assertTrue(found.all { it.property == "workflowVariables" }, "property names the top-level field for UI mapping: $found")
    }

    @Test
    fun `a nested enum is checked`() {
      assertViolations(
        props("workflowVariables" to listOf(mapOf("name" to "a", "value" to "1", "type" to "integer"))),
        knime,
        "enum" to "/workflowVariables/0/type"
      )
    }

    @Test
    fun `nested type errors are reported at the exact path`() {
      assertViolations(
        props("workflowVariables" to listOf(mapOf("name" to "a", "value" to 1, "type" to "int"))),
        knime,
        "type" to "/workflowVariables/0/value"
      )
    }

    @Test
    fun `an item that is not an object is a type error`() {
      assertViolations(props("workflowCredentials" to listOf("prod-db")), knime, "type" to "/workflowCredentials/0")
    }

    @Test
    fun `problems in several nested items and at the top level are all reported`() {
      assertViolations(
        props(
          "timeoutSeconds" to 5,
          "workflowVariables" to listOf(mapOf("name" to "a", "value" to "1", "type" to "float")),
          "workflowCredentials" to listOf(mapOf("name" to "db"))
        ),
        knime,
        "minimum" to "/timeoutSeconds",
        "enum" to "/workflowVariables/0/type",
        "required" to "/workflowCredentials/0"
      )
    }

    @Test
    fun `extra keys inside a nested object are allowed - the schema does not set additionalProperties`() {
      assertValid(props("workflowCredentials" to listOf(mapOf("name" to "db", "credential" to "c", "note" to "x"))), knime)
    }

    @Test
    fun `an empty string satisfies required - the schema has no minLength`() {
      // The node rejects "" itself at run time; the schema check cannot, so start does not 400 on it.
      assertValid(mapOf("workflowLocation" to "", "executionUploadUrl" to ""), knime)
    }

    @Test
    fun `null for a typed field is a type error, not 'absent'`() {
      assertViolations(props("timeoutSeconds" to null), knime, "type" to "/timeoutSeconds")
    }
  }

  // --- dialects: default draft 7, an explicit $schema wins ---

  @Nested
  inner class Dialects {

    /** `ClaudeAIAgentNodeModel` (continuum-feature-ai) `backend`: `oneOf` of `const` titles, the JSONForms enum-with-labels idiom. */
    private fun claudeBackend(dialect: String?) = schema(
      """
      {
        ${dialect?.let { "\"\$schema\": \"$it\"," } ?: ""}
        "type": "object",
        "properties": {
          "backend": { "type": "string", "default": "ANTHROPIC",
                       "oneOf": [ { "const": "ANTHROPIC", "title": "Anthropic (Claude)" },
                                  { "const": "OLLAMA", "title": "Ollama (local)" },
                                  { "const": "CUSTOM", "title": "Custom Anthropic-compatible endpoint" } ] },
          "maxTurns": { "type": "integer", "minimum": 1, "maximum": 200, "default": 10 }
        },
        "required": ["backend"]
      }
      """
    )

    private fun exclusiveMinimum(dialect: String?, value: Any) = schema(
      """
      { ${dialect?.let { "\"\$schema\": \"$it\"," } ?: ""}
        "type": "object", "properties": { "n": { "type": "number", "minimum": 0, "exclusiveMinimum": ${mapper.writeValueAsString(value)} } } }
      """
    )

    private val draft7Ids = listOf(null, "http://json-schema.org/draft-07/schema#", "http://json-schema.org/draft-07/schema")

    @Test
    fun `with no $schema or draft-07 (with or without the trailing hash), oneOf-const works as an enum`() {
      // Same result as the workbench's Ajv for all three.
      draft7Ids.forEach { dialect ->
        assertValid(mapOf("backend" to "OLLAMA", "maxTurns" to 10), claudeBackend(dialect))
        assertViolations(mapOf("backend" to "OPENAI"), claudeBackend(dialect), "oneOf" to "/backend", "const" to "/backend")
      }
    }

    @Test
    fun `explicit draft-04 is honoured - const is unknown there, so oneOf-const rejects every value`() {
      // Backend only: Ajv 8 (draft-07 build) cannot compile a draft-04 schema, so the workbench dialog fails on it.
      val draft4 = claudeBackend("http://json-schema.org/draft-04/schema#")

      assertViolations(mapOf("backend" to "OLLAMA"), draft4, "oneOf" to "/backend")
      assertViolations(mapOf("backend" to "OPENAI"), draft4, "oneOf" to "/backend")
    }

    @Test
    fun `boolean exclusiveMinimum is draft-04 only - an invalid schema under the default`() {
      // Default (draft 7): the schema itself is invalid, reported as one violation, as Ajv also refuses it.
      draft7Ids.forEach { dialect ->
        assertViolations(mapOf("n" to 1), exclusiveMinimum(dialect, true), ValidationHelper.INVALID_SCHEMA_KEYWORD to "")
      }
      // Declared draft-04: valid, and it excludes the minimum.
      val draft4 = exclusiveMinimum("http://json-schema.org/draft-04/schema#", true)
      assertValid(mapOf("n" to 1), draft4)
      assertViolations(mapOf("n" to 0), draft4, "minimum" to "/n")
    }

    @Test
    fun `numeric exclusiveMinimum is the draft-07 form`() {
      val schema = schema("""{ "type": "object", "properties": { "n": { "type": "number", "exclusiveMinimum": 0 } } }""")

      assertValid(mapOf("n" to 0.001), schema)
      assertViolations(mapOf("n" to 0), schema, "exclusiveMinimum" to "/n")
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "https://json-schema.org/draft-07/schema#",
        "http://json-schema.org/draft-06/schema#",
        "https://json-schema.org/draft/2019-09/schema",
        "https://json-schema.org/draft/2020-12/schema"
      ]
    )
    fun `other ids the backend knows also treat oneOf-const as an enum`(dialect: String) {
      // Backend only: the workbench's Ajv (draft-07 build) knows only the http:// draft-07 id and cannot compile
      // these, so a node must not declare them. Includes the https:// spelling of draft-07, which networknt maps to
      // draft 7 but Ajv rejects.
      assertValid(mapOf("backend" to "OLLAMA"), claudeBackend(dialect))
      assertViolations(mapOf("backend" to "OPENAI"), claudeBackend(dialect), "oneOf" to "/backend", "const" to "/backend")
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "http://json-schema.org/draft-03/schema#",
        "https://example.com/my-dialect",
        "not a uri"
      ]
    )
    fun `an unknown $schema is an invalid schema, not a crash and not a silent pass`(dialect: String) {
      // Remote fetching is off, so nothing is downloaded. Ajv also refuses these.
      val found = violations(mapOf("backend" to "ANTHROPIC"), claudeBackend(dialect))

      assertEquals(listOf(ValidationHelper.INVALID_SCHEMA_KEYWORD), found.map { it.keyword }, found.toString())
      assertTrue(found.single().message.startsWith("propertiesSchema is not a valid JSON Schema"), found.single().message)
    }

    @Test
    fun `an invalid schema throws the typed exception from validateJsonWithSchema`() {
      val e = assertThrows<PropertiesValidationException> {
        ValidationHelper.validateJsonWithSchema(mapOf("backend" to "ANTHROPIC"), claudeBackend("https://example.com/my-dialect"))
      }
      assertEquals(ValidationHelper.INVALID_SCHEMA_KEYWORD, e.violations.single().keyword)
    }
  }
}
