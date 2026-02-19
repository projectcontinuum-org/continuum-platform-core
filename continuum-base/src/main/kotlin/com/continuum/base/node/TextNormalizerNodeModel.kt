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

    override val documentationMarkdown = """
        # Text Normalizer
        
        Normalizes text by applying standard cleaning operations: trimming whitespace, converting to lowercase, and removing non-alphanumeric characters (except spaces).
        
        ## Input Ports
        - **data**: Input table with text column to normalize
        
        ## Output Ports
        - **data**: Input table with added normalized text column
        
        ## Properties
        - **inputCol** (string, required): Column containing text to normalize
        - **outputCol** (string, required): Column name for the normalized output
        
        ## Behavior
        For each row, applies the following transformations in sequence:
        1. **Trim**: Removes leading and trailing whitespace
        2. **Lowercase**: Converts all characters to lowercase
        3. **Strip**: Removes all non-alphanumeric characters except spaces (keeps a-z, 0-9, and spaces)
        
        The original column is preserved, and normalized text is added as a new column.
        
        ## Use Cases
        - Text preprocessing for machine learning
        - Search normalization
        - Data deduplication
        - Consistency enforcement
        
        ## Example
        
        **Input:**
        ```json
        [
          {"id": 1, "text": "Hello, World! 123"},
          {"id": 2, "text": "  Foo Bar @baz  "}
        ]
        ```
        
        **Properties:**
        ```json
        {
          "inputCol": "text",
          "outputCol": "clean"
        }
        ```
        
        **Output:**
        ```json
        [
          {"id": 1, "text": "Hello, World! 123", "clean": "hello world 123"},
          {"id": 2, "text": "  Foo Bar @baz  ", "clean": "foo bar baz"}
        ]
        ```
        
        Note how punctuation and special characters are removed, while numbers and spaces are preserved.
    """.trimIndent()

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
