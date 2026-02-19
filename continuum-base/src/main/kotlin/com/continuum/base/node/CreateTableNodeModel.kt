package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.TriggerNodeModel
import com.continuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import freemarker.template.Configuration
import freemarker.template.Template
import freemarker.template.TemplateExceptionHandler
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component
import java.io.StringReader
import java.io.StringWriter

@Component
class CreateTableNodeModel : TriggerNodeModel() {
  companion object {
    private val LOGGER = LoggerFactory.getLogger(CreateTableNodeModel::class.java)
    private val objectMapper = ObjectMapper()
    private val freemarkerConfig = Configuration(Configuration.VERSION_2_3_32).apply {
      defaultEncoding = "UTF-8"
      templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
      logTemplateExceptions = false
      wrapUncheckedExceptions = true
      fallbackOnNullLoopVariable = false
      numberFormat = "computer"  // Outputs: 1000, 100, not 1,000, 1,00
    }
  }

  final override val outputPorts = mapOf(
    "data" to ContinuumWorkflowModel.NodePort(
      name = "output table",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  override val categories = listOf(
    "Table & Data Structures"
  )

  val propertiesSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "object",
          "properties": {
            "jsonArrayString": {
              "type": "string",
              "title": "JSON Array",
              "description": "JSON array string where each object becomes a table row"
            }
          },
          "required": ["jsonArrayString"]
        }
        """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  val propertiesUiSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "VerticalLayout",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/jsonArrayString",
              "options": {
                "format": "code",
                "language": "freemarker",
                "rows": 15
              }
            }
          ]
        }
        """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  override val metadata = ContinuumWorkflowModel.NodeData(
    id = this.javaClass.name,
    description = "Creates a structured table from FreeMarker template configuration",
    title = "Create Table",
    subTitle = "Generate table rows from template",
    nodeModel = this.javaClass.name,
    icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5" />
            </svg>
        """.trimIndent(),
    outputs = outputPorts,
    properties = mapOf(
      "jsonArrayString" to "[<#list 1..2 as i>{\"id\": \${i}, \"name\": \"User\${i}\"}<#if i?has_next>,</#if></#list>]"
    ),
    propertiesSchema = propertiesSchema,
    propertiesUISchema = propertiesUiSchema
  )

  override fun execute(
    properties: Map<String, Any>?,
    nodeOutputWriter: NodeOutputWriter
  ) {
    val jsonArrayString = properties?.get("jsonArrayString") as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "jsonArrayString is not provided",
      isRetriable = false
    )

    if (jsonArrayString.trim().isEmpty()) {
      LOGGER.info("Empty template string provided, returning empty table")
      return
    }

    LOGGER.info("Rendering FreeMarker template of length: ${jsonArrayString.length}")

    // Render FreeMarker template
    val renderedJson: String = try {
      val template = Template("json", StringReader(jsonArrayString), freemarkerConfig)
      val writer = StringWriter()
      template.process(emptyMap<String, Any>(), writer)
      writer.toString()
    } catch (e: Exception) {
      LOGGER.error("Failed to render template: ${e.message}")
      throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "Template rendering failed: ${e.message}",
        isRetriable = false
      )
    }

    if (renderedJson.trim().isEmpty()) {
      LOGGER.info("Template rendered to empty string, returning empty table")
      return
    }

    LOGGER.debug("Rendered JSON: $renderedJson")

    // Parse rendered JSON array
    val jsonArray: List<Map<String, Any?>> = try {
      val parsed = objectMapper.readValue(renderedJson, object : TypeReference<List<Map<String, Any?>>>() {})
      parsed
    } catch (e: Exception) {
      LOGGER.error("Failed to parse JSON array: ${e.message}")
      throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "Invalid JSON array format: ${e.message}",
        isRetriable = false
      )
    }

    if (jsonArray.isEmpty()) {
      LOGGER.info("JSON array is empty, returning empty table")
      return
    }

    // Collect all unique keys across all objects to ensure consistent columns
    val allKeys = mutableSetOf<String>()
    jsonArray.forEach { obj ->
      allKeys.addAll(obj.keys)
    }

    LOGGER.info("Creating table with ${jsonArray.size} rows and ${allKeys.size} columns: $allKeys")

    // Create consistent rows with all columns (preserve original types from JSON)
    nodeOutputWriter.createOutputPortWriter("data").use { writer ->
      jsonArray.forEachIndexed { index, obj ->
        val consistentRow = mutableMapOf<String, Any>()
        allKeys.forEach { key ->
          val value = obj[key]
          consistentRow[key] = value ?: ""
        }
        writer.write(index.toLong(), consistentRow)
      }
    }

    LOGGER.info("Successfully created table with ${jsonArray.size} rows")
  }
}
