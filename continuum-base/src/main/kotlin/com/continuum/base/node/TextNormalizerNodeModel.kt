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
class TextNormalizerNodeModel: ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TextNormalizerNodeModel::class.java)
        private val objectMapper = ObjectMapper()
        private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9 ]")
    }

    final override val inputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "normalized table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    override val categories = listOf(
        "String & Text"
    )

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "inputCol": {
              "type": "string",
              "title": "Input Column",
              "description": "The column containing text to normalize"
            },
            "outputCol": {
              "type": "string",
              "title": "Output Column",
              "description": "The column to write normalized text to"
            }
          },
          "required": ["inputCol", "outputCol"]
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
              "scope": "#/properties/inputCol"
            },
            {
              "type": "Control",
              "scope": "#/properties/outputCol"
            }
          ]
        }
        """.trimIndent(),
        object: TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Normalizes text by trimming, lowercasing, and removing non-alphanumeric characters",
        title = "Text Normalizer",
        subTitle = "Clean and normalize text",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25H12" />
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "inputCol" to "text",
            "outputCol" to "clean"
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val inputCol = properties?.get("inputCol") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "inputCol is not provided"
        )
        val outputCol = properties["outputCol"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "outputCol is not provided"
        )
        
        LOGGER.info("Normalizing text from column '$inputCol' to '$outputCol'")
        
        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
            inputs["data"]?.use { reader ->
                var row = reader.read()
                var rowNumber = 0L
                
                while (row != null) {
                    val text = row[inputCol]?.toString() ?: ""
                    val cleaned = text.trim()
                        .lowercase()
                        .replace(NON_ALPHANUM_REGEX, "")
                    
                    val newRow = row.toMutableMap().apply {
                        this[outputCol] = cleaned
                    }
                    
                    writer.write(rowNumber, newRow)
                    rowNumber++
                    row = reader.read()
                }
                
                LOGGER.info("Normalized $rowNumber rows")
            }
        }
    }
}
