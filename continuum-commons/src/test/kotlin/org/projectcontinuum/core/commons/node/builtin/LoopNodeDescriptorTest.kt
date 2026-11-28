package org.projectcontinuum.core.commons.node.builtin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.utils.ValidationHelper

/** The Loop's registered definition matches the contract the workbench renders: schema, UI schema, defaults, documentation. */
class LoopNodeDescriptorTest {

  private val schema = LoopNodeDescriptor.propertiesSchema

  private fun violationsOf(overrides: Map<String, Any?>): List<ValidationHelper.SchemaViolation> {
    val properties: Map<String, Any?> = LoopNodeDescriptor.defaultProperties + overrides
    return ValidationHelper.findSchemaViolations(properties, schema)
  }

  private fun assertOneViolationOn(property: String, overrides: Map<String, Any?>) {
    val violations = violationsOf(overrides = overrides)
    assertEquals(1, violations.size, "exactly one violation for $overrides: $violations")
    assertEquals(property, violations.single().property)
  }

  @Test
  fun `the defaults validate against the schema`() {
    assertEquals(emptyList<ValidationHelper.SchemaViolation>(), violationsOf(overrides = emptyMap()))
  }

  @Test
  fun `the defaults are the contract values`() {
    assertEquals(
      mapOf(
        "condition" to "vars[\"hasMore\"] == true",
        "maxIterations" to 50,
        "onLimit" to "fail",
        "onNonBooleanResult" to "fail",
        "check" to "after"
      ),
      LoopNodeDescriptor.defaultProperties
    )
  }

  @Test
  fun `maxIterations outside 1 to 10000 gives one violation on maxIterations`() {
    assertOneViolationOn(property = "maxIterations", overrides = mapOf("maxIterations" to 0))
    assertOneViolationOn(property = "maxIterations", overrides = mapOf("maxIterations" to 10_001))
    assertEquals(emptyList<ValidationHelper.SchemaViolation>(), violationsOf(overrides = mapOf("maxIterations" to 1)))
    assertEquals(emptyList<ValidationHelper.SchemaViolation>(), violationsOf(overrides = mapOf("maxIterations" to 10_000)))
  }

  @Test
  fun `onLimit and check outside their enums give one violation each`() {
    assertOneViolationOn(property = "onLimit", overrides = mapOf("onLimit" to "ignore"))
    assertOneViolationOn(property = "check", overrides = mapOf("check" to "during"))
    assertEquals(
      emptyList<ValidationHelper.SchemaViolation>(),
      violationsOf(overrides = mapOf("onLimit" to "stop-with-warning", "check" to "before"))
    )
  }

  @Test
  fun `onNonBooleanResult outside its enum gives one violation and both values are valid`() {
    assertOneViolationOn(property = "onNonBooleanResult", overrides = mapOf("onNonBooleanResult" to "maybe"))
    assertEquals(emptyList<ValidationHelper.SchemaViolation>(), violationsOf(overrides = mapOf("onNonBooleanResult" to "fail")))
    assertEquals(emptyList<ValidationHelper.SchemaViolation>(), violationsOf(overrides = mapOf("onNonBooleanResult" to "treat-as-false")))
  }

  @Test
  fun `onNonBooleanResult is required`() {
    val withoutIt = LoopNodeDescriptor.defaultProperties - "onNonBooleanResult"
    val violations = ValidationHelper.findSchemaViolations(withoutIt, schema)

    assertEquals(1, violations.size, violations.toString())
    assertEquals("required", violations.single().keyword)
  }

  @Test
  fun `an empty condition and one over 2048 characters give one violation on condition`() {
    assertOneViolationOn(property = "condition", overrides = mapOf("condition" to ""))
    assertOneViolationOn(property = "condition", overrides = mapOf("condition" to "x".repeat(2049)))
    assertEquals(emptyList<ValidationHelper.SchemaViolation>(), violationsOf(overrides = mapOf("condition" to "x".repeat(2048))))
  }

  @Test
  fun `the schema matches the contract shape`() {
    assertNull(schema["\$schema"], "no \$schema, so draft 7 applies in both validators")
    assertEquals("object", schema["type"])
    assertEquals(false, schema["additionalProperties"])
    assertEquals(listOf("condition", "maxIterations", "onLimit", "onNonBooleanResult", "check"), schema["required"])

    val properties = schema["properties"] as Map<*, *>
    val condition = properties["condition"] as Map<*, *>
    val maxIterations = properties["maxIterations"] as Map<*, *>
    assertEquals(mapOf("type" to "string", "minLength" to 1, "maxLength" to 2048), condition.filterKeys { key -> key in setOf("type", "minLength", "maxLength") })
    assertEquals(mapOf("type" to "integer", "minimum" to 1, "maximum" to 10_000), maxIterations.filterKeys { key -> key in setOf("type", "minimum", "maximum") })
    assertEquals(listOf("fail", "stop-with-warning"), (properties["onLimit"] as Map<*, *>)["enum"])
    assertEquals(listOf("before", "after"), (properties["check"] as Map<*, *>)["enum"])
    assertEquals(listOf("fail", "treat-as-false"), (properties["onNonBooleanResult"] as Map<*, *>)["enum"])
    properties.forEach { (propertyName, propertySchema) ->
      val propertyMap = propertySchema as Map<*, *>
      assertTrue(propertyMap["title"] is String, "title of $propertyName")
      assertTrue(propertyMap["description"] is String, "description of $propertyName")
      assertEquals(LoopNodeDescriptor.defaultProperties[propertyName], propertyMap["default"], "default of $propertyName")
    }
  }

  @Test
  fun `an undeclared property is rejected`() {
    val violations = violationsOf(overrides = mapOf("extra" to 1))

    assertEquals(1, violations.size, violations.toString())
    assertEquals("additionalProperties", violations.single().keyword)
  }

  @Test
  fun `the UI schema routes the condition to the query renderer, then the other four controls with onNonBooleanResult after onLimit`() {
    val uiSchema = LoopNodeDescriptor.propertiesUISchema
    val elements = uiSchema["elements"] as List<*>
    val scopes = elements.map { element -> (element as Map<*, *>)["scope"] }
    val conditionControl = elements.first() as Map<*, *>

    assertEquals("VerticalLayout", uiSchema["type"])
    assertEquals(
      listOf(
        "#/properties/condition",
        "#/properties/maxIterations",
        "#/properties/onLimit",
        "#/properties/onNonBooleanResult",
        "#/properties/check"
      ),
      scopes
    )
    assertEquals(mapOf("format" to "query", "language" to "CEL"), conditionControl["options"])
  }

  @Test
  fun `the node data is the built-in loop with no ports`() {
    val nodeData = LoopNodeDescriptor.nodeData

    assertEquals(LoopNode.NODE_MODEL, nodeData.id)
    assertEquals(LoopNode.NODE_MODEL, nodeData.nodeModel)
    assertEquals("Loop", nodeData.title)
    assertEquals("Repeat the nodes inside", nodeData.subTitle)
    assertNull(nodeData.inputs)
    assertNull(nodeData.outputs)
    assertTrue(nodeData.icon.orEmpty().startsWith("<svg"))
    assertEquals(LoopNodeDescriptor.defaultProperties, nodeData.properties)
    assertEquals(listOf("Flow Control"), LoopNodeDescriptor.categories)
  }

  @Test
  fun `the documentation resource loads and explains full CEL conditions`() {
    val documentation = LoopNodeDescriptor.documentationMarkdown

    assertTrue(documentation.startsWith("# Loop"))
    assertTrue(documentation.contains("vars[\"hasMore\"] == true"))
    assertTrue(documentation.contains("vars[\"page\"] < 10 && vars[\"hasMore\"]"))
    assertTrue(documentation.contains("no ports"))
    assertTrue(documentation.contains("onNonBooleanResult"))
    assertTrue(documentation.contains("treat-as-false"))
    assertTrue(!documentation.contains("one flow variable compared"), "no single-comparison wording")
    assertTrue(!documentation.contains("Not supported"), "no list of excluded CEL features")
  }

  @Test
  fun `the condition description teaches CEL over vars`() {
    val condition = (schema["properties"] as Map<*, *>)["condition"] as Map<*, *>
    val description = condition["description"] as String

    assertTrue(description.startsWith("Repeat while this CEL expression is true."), description)
    assertTrue(LoopNodeDescriptor.nodeData.description.contains("CEL condition over flow variables"))
  }
}
