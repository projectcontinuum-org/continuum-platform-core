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
class ConditionalSplitterNodeModel: ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ConditionalSplitterNodeModel::class.java)
        private val objectMapper = ObjectMapper()
    }

    final override val inputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "high" to ContinuumWorkflowModel.NodePort(
            name = "high values (>= threshold)",
            contentType = TEXT_PLAIN_VALUE
        ),
        "low" to ContinuumWorkflowModel.NodePort(
            name = "low values (< threshold)",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    override val categories = listOf(
        "Flow Control"
    )

    override val documentationMarkdown = """
        # Conditional Splitter
        
        Splits input rows into two separate output streams based on a numeric threshold comparison, enabling conditional workflow branching.
        
        ## Input Ports
        - **data**: Input table to split
        
        ## Output Ports
        - **high**: Rows where `column >= threshold`
        - **low**: Rows where `column < threshold`
        
        ## Properties
        - **column** (string, required): Column name to compare against threshold
        - **threshold** (number, required): Split point value
        
        ## Behavior
        For each input row:
        1. Extracts the value from the specified `column`
        2. Converts to number (defaults to 0 if not numeric)
        3. Compares to `threshold`:
           - If `value >= threshold` → routes to **high** port
           - If `value < threshold` → routes to **low** port
        
        Each output port maintains independent row numbering starting from 0.
        
        ## Use Cases
        - Route high-value vs low-value transactions
        - Separate pass/fail test results
        - Split data for different processing pipelines
        - A/B testing based on scores
        
        ## Example
        
        **Input:**
        ```json
        [
          {"id": 1, "value": 10},
          {"id": 2, "value": 20},
          {"id": 3, "value": 15}
        ]
        ```
        
        **Properties:**
        ```json
        {
          "column": "value",
          "threshold": 15
        }
        ```
        
        **Output (high port):**
        ```json
        [
          {"id": 2, "value": 20},
          {"id": 3, "value": 15}
        ]
        ```
        
        **Output (low port):**
        ```json
        [
          {"id": 1, "value": 10}
        ]
        ```
        
        Note: value=15 goes to "high" because 15 >= 15 is true.
    """.trimIndent()

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "column": {
              "type": "string",
              "title": "Column Name",
              "description": "The column to compare against threshold"
            },
            "threshold": {
              "type": "number",
              "title": "Threshold",
              "description": "Split point: high (>= threshold), low (< threshold)"
            }
          },
          "required": ["column", "threshold"]
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
              "scope": "#/properties/column"
            },
            {
              "type": "Control",
              "scope": "#/properties/threshold"
            }
          ]
        }
        """.trimIndent(),
        object: TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Splits rows into two outputs based on threshold comparison",
        title = "Conditional Splitter",
        subTitle = "Split by threshold",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 7.5L7.5 3m0 0L12 7.5M7.5 3v13.5m13.5 0L16.5 21m0 0L12 16.5m4.5 4.5V7.5" />
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "column" to "value",
            "threshold" to 15
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val column = properties?.get("column") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "column is not provided"
        )
        val threshold = (properties["threshold"] as? Number)?.toDouble() ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "threshold is not provided"
        )
        
        LOGGER.info("Splitting rows on column '$column' with threshold $threshold")
        
        var highCount = 0L
        var lowCount = 0L
        
        // Create both output writers
        nodeOutputWriter.createOutputPortWriter("high").use { highWriter ->
            nodeOutputWriter.createOutputPortWriter("low").use { lowWriter ->
                inputs["data"]?.use { reader ->
                    var row = reader.read()
                    
                    while (row != null) {
                        val value = (row[column] as? Number)?.toDouble() ?: 0.0
                        
                        if (value >= threshold) {
                            highWriter.write(highCount, row)
                            highCount++
                        } else {
                            lowWriter.write(lowCount, row)
                            lowCount++
                        }
                        
                        row = reader.read()
                    }
                }
            }
        }
        
        LOGGER.info("Split complete: $highCount rows to 'high', $lowCount rows to 'low'")
    }
}
