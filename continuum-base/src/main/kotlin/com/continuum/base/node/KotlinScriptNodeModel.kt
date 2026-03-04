package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.prototol.progress.NodeProgressCallback
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import javax.script.ScriptEngineManager

@Component
class KotlinScriptNodeModel : ProcessNodeModel() {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(KotlinScriptNodeModel::class.java)
    private val objectMapper = ObjectMapper()
  }

  final override val inputPorts = mapOf(
    "data" to ContinuumWorkflowModel.NodePort(
      name = "input table",
      contentType = MediaType.APPLICATION_JSON_VALUE
    )
  )

  final override val outputPorts = mapOf(
    "data" to ContinuumWorkflowModel.NodePort(
      name = "enriched table",
      contentType = MediaType.APPLICATION_JSON_VALUE
    )
  )

  override val categories = listOf("Transform")

  val propertiesSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "object",
          "properties": {
            "script": {
              "type": "string",
              "title": "Kotlin Script",
              "description": "Kotlin script to evaluate (use 'row' variable for current row)",
              "minLength": 1
            }
          },
          "required": ["script"]
        }
        """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  val uiSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "VerticalLayout",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/script",
              "options": {
                "format": "code",
                "language": "kotlin",
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
    description = "Run a Kotlin script for each row, adding script_result column",
    title = "Kotlin Script",
    subTitle = "Evaluate Kotlin script per row",
    nodeModel = this.javaClass.name,
    icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
              <path d="M4.456 5.25a1.5 1.5 0 0 1 1.5-1.5h12.586c.89 0 1.337 1.077.707 1.707L12.706 12l6.543 6.543c.63.63.184 1.707-.707 1.707H5.956a1.5 1.5 0 0 1-1.06-.44a1.5 1.5 0 0 1-.44-1.06zm8.25-1.5L4.456 12m8.25 0l-7.81 7.81" fill="none" stroke="currentColor" strokeLinejoin="round" strokeWidth="1.5"/>
            </svg>
        """.trimIndent(),
    inputs = inputPorts,
    outputs = outputPorts,
    properties = mapOf("script" to "(row[\"message\"]?.toString() ?: \"\") + \"_result\""),
    propertiesSchema = propertiesSchema,
    propertiesUISchema = uiSchema
  )

  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback
  ) {
    val dataReader = inputs["data"] ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "Data port required"
    )

    // Get script from properties
    val scriptText = (properties?.get("script") as? String)
      ?.takeIf { it.isNotBlank() }
      ?: throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "Script property is required and cannot be empty"
      )

    // Create Kotlin script engine
    val engine = ScriptEngineManager().getEngineByName("kotlin")
      ?: throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "Kotlin script engine not found"
      )

    nodeOutputWriter.createOutputPortWriter("data").use { writer ->
      dataReader.use { reader ->
        var rowNumber = 0L
        var row = reader.read()
        while (row != null) {
          try {
            val bindings = engine.createBindings().apply {
              put("row", row)
            }
            val scriptResult = engine.eval(scriptText, bindings)
            val enrichedRow = row.toMutableMap().apply {
              put("script_result", scriptResult)
            }
            writer.write(rowNumber, enrichedRow)
            rowNumber++
          } catch (e: Exception) {
            throw NodeRuntimeException(
              workflowId = "",
              nodeId = "",
              message = "Script execution error at row $rowNumber: ${e.message}"
            )
          }
          row = reader.read()
        }
      }
    }
  }
}