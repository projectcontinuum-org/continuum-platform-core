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
class DynamicRowFilterNodeModel: ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(DynamicRowFilterNodeModel::class.java)
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
            name = "filtered table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    override val categories = listOf(
        "Filter & Select"
    )

    override val documentationMarkdown = """
        # Dynamic Row Filter
        
        Filters table rows based on a numeric threshold comparison, keeping only rows where the specified column value is greater than the threshold.
        
        ## Input Ports
        - **data**: Input table to filter
        
        ## Output Ports
        - **data**: Filtered table containing only rows that meet the criteria
        
        ## Properties
        - **columnName** (string, required): The column name to compare against the threshold
        - **threshold** (number, required): The numeric threshold value for comparison
        
        ## Behavior
        Iterates through each row and:
        1. Reads the value from the specified `columnName`
        2. Converts it to a number (defaults to 0 if not a number)
        3. Compares: if `value > threshold`, includes the row in output
        4. If `value <= threshold`, excludes the row from output
        
        Only matching rows are written to the output, reducing data volume for downstream processing.
        
        ## Example
        
        **Input:**
        ```json
        [
          {"id": 1, "age": 25, "name": "Alice"},
          {"id": 2, "age": 35, "name": "Bob"},
          {"id": 3, "age": 28, "name": "Charlie"}
        ]
        ```
        
        **Properties:**
        ```json
        {
          "columnName": "age",
          "threshold": 30
        }
        ```
        
        **Output:**
        ```json
        [
          {"id": 2, "age": 35, "name": "Bob"}
        ]
        ```
        
        Only Bob's row is included because age (35) > 30.
    """.trimIndent()

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "columnName": {
              "type": "string",
              "title": "Column Name",
              "description": "The column to compare against the threshold"
            },
            "threshold": {
              "type": "number",
              "title": "Threshold",
              "description": "Only rows where column value > threshold will be included"
            }
          },
          "required": ["columnName", "threshold"]
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
              "scope": "#/properties/columnName"
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
        description = "Filters rows where the specified column value is greater than the threshold",
        title = "Dynamic Row Filter",
        subTitle = "Filter rows by threshold",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 3c2.755 0 5.455.232 8.083.678.533.09.917.556.917 1.096v1.044a2.25 2.25 0 01-.659 1.591l-5.432 5.432a2.25 2.25 0 00-.659 1.591v2.927a2.25 2.25 0 01-1.244 2.013L9.75 21v-6.568a2.25 2.25 0 00-.659-1.591L3.659 7.409A2.25 2.25 0 013 5.818V4.774c0-.54.384-1.006.917-1.096A48.32 48.32 0 0112 3z" />
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "columnName" to "age",
            "threshold" to 30
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val columnName = properties?.get("columnName") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "columnName is not provided"
        )
        val threshold = (properties["threshold"] as? Number)?.toDouble() ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "threshold is not provided"
        )
        
        LOGGER.info("Filtering rows where $columnName > $threshold")
        
        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
            inputs["data"]?.use { reader ->
                var row = reader.read()
                var rowNumber = 0L
                
                while (row != null) {
                    val value = (row[columnName] as? Number)?.toDouble() ?: 0.0
                    
                    if (value > threshold) {
                        writer.write(rowNumber, row)
                        rowNumber++
                    }
                    
                    row = reader.read()
                }
                
                LOGGER.info("Filtered to $rowNumber rows")
            }
        }
    }
}
