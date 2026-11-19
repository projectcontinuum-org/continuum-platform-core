package org.projectcontinuum.core.worker.starter.activity

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.ValidationHelper

/**
 * `test-1.cwf.json` must stay runnable now that properties are validated against the node's registered schema
 * (the worker uses its own `metadata.propertiesSchema`, not the copy in the file).
 */
class WorkflowFixturePropertiesTest {

  private val splitSchema: Map<String, Any> = mapOf(
    "type" to "object",
    "properties" to mapOf(
      "columnName" to mapOf("type" to "string"),
      "outputs" to mapOf(
        "type" to "array",
        "items" to mapOf(
          "type" to "object",
          "properties" to mapOf("columnName" to mapOf("type" to "string")),
          "required" to listOf("columnName")
        )
      )
    ),
    "required" to listOf("columnName")
  )

  private val workflow: ContinuumWorkflowModel =
    ObjectMapper().registerModule(KotlinModule.Builder().build()).readValue(
      javaClass.getResourceAsStream("/test-workflows/test-1.cwf.json"),
      ContinuumWorkflowModel::class.java
    )

  private fun node(simpleName: String) = workflow.nodes.single { it.data.nodeModel.endsWith(".$simpleName") }

  @Test
  fun `split node properties satisfy the registered SplitNodeModel schema`() {
    val violations = ValidationHelper.findSchemaViolations(node("SplitNodeModel").data.properties, splitSchema)
    assertTrue(violations.isEmpty(), violations.toString())
  }

  @Test
  fun `time trigger properties satisfy the schema the fixture declares`() {
    val trigger = node("TimeTriggerNodeModel")
    assertFalse(trigger.data.propertiesSchema.isEmpty())
    val violations = ValidationHelper.findSchemaViolations(trigger.data.properties, trigger.data.propertiesSchema)
    assertTrue(violations.isEmpty(), violations.toString())
  }

  @Test
  fun `the old empty split properties would be rejected`() {
    val violations = ValidationHelper.findSchemaViolations(emptyMap(), splitSchema)
    assertTrue(violations.any { it.keyword == "required" }, violations.toString())
  }
}
