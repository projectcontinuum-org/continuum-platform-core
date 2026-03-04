package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.prototol.progress.NodeProgressCallback
import com.continuum.core.commons.utils.NodeInputReader
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
class CreateTableNodeModel : ProcessNodeModel() {
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

  override val inputPorts = emptyMap<String, ContinuumWorkflowModel.NodePort>()

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
            	<path d="M21.5 2h-19a.5.5 0 0 0-.5.5v19a.5.5 0 0 0 .5.5h19a.5.5 0 0 0 .5-.5v-19a.5.5 0 0 0-.5-.5zm-13 19H3v-5.5h5.5V21zm0-6.5H3v-5h5.5v5zm0-6H3V3h5.5v5.5zm6 12.5h-5v-5.5h5V21zm0-6.5h-5v-5h5v5zm0-6h-5V3h5v5.5zM21 21h-5.5v-5.5H21V21zm0-6.5h-5.5v-5H21v5zm0-6h-5.5V3H21v5.5z" fill="currentColor"/>
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
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback
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
