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
class PivotColumnsNodeModel: ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PivotColumnsNodeModel::class.java)
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
            name = "pivoted table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    override val categories = listOf(
        "Transform"
    )

    override val documentationMarkdown = """
        # Pivot Columns
        
        Transforms a table by pivoting: unique values from a pivot column become new column headers, with values from a value column filling the cells, grouped by an index column.
        
        ## Input Ports
        | Port | Type | Format | Description |
        |------|------|--------|-------------|
        | data | Table | List<Map<String, Any>> | Input table in long format with rows to pivot |
        
        ## Output Ports
        | Port | Type | Format | Description |
        |------|------|--------|-------------|
        | data | Table | List<Map<String, Any>> | Pivoted table in wide format with unique column values as headers |
        
        ## Properties
        - **indexCol** (string, required): Column to use as row identifier/index
        - **pivotCol** (string, required): Column whose unique values become new column names
        - **valueCol** (string, required): Column containing values to fill the pivoted cells
        
        ## Behavior
        1. Groups all rows by `indexCol` value
        2. Identifies all unique values in `pivotCol`
        3. For each group, creates a new row with:
           - The `indexCol` value
           - One column for each unique `pivotCol` value
           - Fills cells with corresponding `valueCol` values
        
        This transforms long-format data (many rows) into wide-format data (fewer rows, more columns).
        
        ## Example
        
        **Input:**
        ```json
        [
          {"day": "Mon", "type": "sales", "value": 100},
          {"day": "Mon", "type": "costs", "value": 50},
          {"day": "Tue", "type": "sales", "value": 200},
          {"day": "Tue", "type": "costs", "value": 75}
        ]
        ```
        
        **Properties:**
        ```json
        {
          "indexCol": "day",
          "pivotCol": "type",
          "valueCol": "value"
        }
        ```
        
        **Output:**
        ```json
        [
          {"day": "Mon", "sales": 100, "costs": 50},
          {"day": "Tue", "sales": 200, "costs": 75}
        ]
        ```
        
        The "type" values (sales, costs) became column names, grouped by day.
    """.trimIndent()

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "indexCol": {
              "type": "string",
              "title": "Index Column",
              "description": "The column to use as row index"
            },
            "valueCol": {
              "type": "string",
              "title": "Value Column",
              "description": "The column containing values to fill pivoted cells"
            },
            "pivotCol": {
              "type": "string",
              "title": "Pivot Column",
              "description": "The column whose unique values become new columns"
            }
          },
          "required": ["indexCol", "valueCol", "pivotCol"]
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
              "scope": "#/properties/indexCol"
            },
            {
              "type": "Control",
              "scope": "#/properties/pivotCol"
            },
            {
              "type": "Control",
              "scope": "#/properties/valueCol"
            }
          ]
        }
        """.trimIndent(),
        object: TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Pivots table so pivot column values become new columns with value column as cell values",
        title = "Pivot Columns",
        subTitle = "Transpose rows to columns",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M7.5 21L3 16.5m0 0L7.5 12M3 16.5h13.5m0-13.5L21 7.5m0 0L16.5 12M21 7.5H7.5" />
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "indexCol" to "day",
            "valueCol" to "value",
            "pivotCol" to "type"
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val indexCol = properties?.get("indexCol") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "indexCol is not provided"
        )
        val valueCol = properties["valueCol"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "valueCol is not provided"
        )
        val pivotCol = properties["pivotCol"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "pivotCol is not provided"
        )
        
        LOGGER.info("Pivoting table: index=$indexCol, pivot=$pivotCol, value=$valueCol")
        
        // Read all rows first (pivot requires full dataset)
        val allRows = mutableListOf<Map<String, Any>>()
        inputs["data"]?.use { reader ->
            var row = reader.read()
            while (row != null) {
                allRows.add(row)
                row = reader.read()
            }
        }
        
        if (allRows.isEmpty()) {
            LOGGER.info("No rows to pivot")
            return
        }
        
        // Find unique pivot values
        val uniquePivotValues = allRows.mapNotNull { it[pivotCol] as? String }.distinct().sorted()
        LOGGER.info("Found ${uniquePivotValues.size} unique pivot values: $uniquePivotValues")
        
        // Group by index column
        val grouped = allRows.groupBy { it[indexCol] }
        
        // Build pivoted rows
        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
            var rowNumber = 0L
            
            grouped.forEach { (indexValue, rows) ->
                val pivotedRow = mutableMapOf<String, Any>(indexCol to (indexValue ?: ""))
                
                // For each unique pivot value, find corresponding value
                uniquePivotValues.forEach { pivotValue ->
                    val matchingRow = rows.find { it[pivotCol] == pivotValue }
                    val cellValue = matchingRow?.get(valueCol)
                    if (cellValue != null) {
                        pivotedRow[pivotValue] = cellValue
                    }
                }
                
                writer.write(rowNumber, pivotedRow)
                rowNumber++
            }
            
            LOGGER.info("Pivoted to $rowNumber rows with ${uniquePivotValues.size} new columns")
        }
    }
}
