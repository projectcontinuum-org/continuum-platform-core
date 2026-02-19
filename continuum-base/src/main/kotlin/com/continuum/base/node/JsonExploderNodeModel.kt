package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component

@Component
class JsonExploderNodeModel: ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(JsonExploderNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "exploded table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    override val categories = listOf(
        "JSON & Data Parsing"
    )

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "jsonCol": {
              "type": "string",
              "title": "JSON Column",
              "description": "Column containing JSON strings"
            }
          },
          "required": ["jsonCol"]
        }
        """.trimIndent(),
        object: TypeReference<Map<String, Any>>() {}
    )

    val propertiesUiSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "VerticalLayout",
          "elements": [
            {
              "type": "Control",
              "scope": "#/properties/jsonCol"
            }
          ]
        }
        """.trimIndent(),
        object: TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Parses JSON strings and flattens keys into new columns",
        title = "JSON Exploder",
        subTitle = "Parse and flatten JSON",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M17.25 6.75L22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3l-4.5 16.5" />
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "jsonCol" to "json"
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val jsonCol = properties?.get("jsonCol") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "jsonCol is not provided"
        )
        
        LOGGER.info("Exploding JSON from column: $jsonCol")
        
        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
            inputs["data"]?.use { reader ->
                var row = reader.read()
                var rowNumber = 0L
                
                while (row != null) {
                    try {
                        val jsonString = row[jsonCol]?.toString() ?: ""
                        
                        if (jsonString.isNotEmpty()) {
                            // Parse JSON string
                            val parsed = objectMapper.readValue(
                                jsonString,
                                object: TypeReference<Map<String, Any>>() {}
                            )
                            
                            // Create new row with parsed values, remove original JSON column
                            val newRow = row.toMutableMap().apply {
                                putAll(parsed)
                                remove(jsonCol)
                            }
                            
                            writer.write(rowNumber, newRow)
                        } else {
                            // If JSON is empty, just remove the column
                            val newRow = row.toMutableMap().apply {
                                remove(jsonCol)
                            }
                            writer.write(rowNumber, newRow)
                        }
                        
                        rowNumber++
                    } catch (e: Exception) {
                        LOGGER.error("Failed to parse JSON in row $rowNumber: ${e.message}")
                        throw NodeRuntimeException(
                            isRetriable = !(e is com.fasterxml.jackson.core.JsonParseException || e is com.fasterxml.jackson.databind.JsonMappingException),
                            workflowId = "",
                            nodeId = "",
                            message = "Failed to parse JSON in row $rowNumber: ${e.message}"
                        )
                    }
                    
                    row = reader.read()
                }
                
                LOGGER.info("Exploded JSON for $rowNumber rows")
            }
        }
    }
}
