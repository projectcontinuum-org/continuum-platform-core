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
import kotlin.math.pow
import kotlin.math.sqrt

@Component
class AnomalyDetectorZScoreNodeModel: ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AnomalyDetectorZScoreNodeModel::class.java)
        private val objectMapper = ObjectMapper()
        private const val Z_THRESHOLD = 2.0
    }

    final override val inputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "table with outlier flags",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    override val categories = listOf(
        "Analysis & Statistics"
    )

    override val documentationMarkdown = """
        # Anomaly Detector (Z-Score)
        
        Detects statistical outliers in numeric data using the Z-score method, flagging values that deviate significantly from the mean.
        
        ## Input Ports
        - **data**: Input table with numeric column to analyze
        
        ## Output Ports
        - **data**: Input table with added `is_outlier` boolean column
        
        ## Properties
        - **valueCol** (string, required): The numeric column to analyze for outliers
        
        ## Behavior
        Uses a two-pass algorithm:
        
        **First Pass:**
        1. Collects all values from `valueCol`
        2. Calculates mean (μ) and standard deviation (σ)
        
        **Second Pass:**
        1. For each row, calculates Z-score: `z = (value - μ) / σ`
        2. Flags as outlier if `|z| > 2.0`
        3. Adds `is_outlier` boolean column to each row
        
        **Edge Cases:**
        - If σ = 0 (all values identical), all rows flagged as `is_outlier: false`
        - Non-numeric values treated as 0
        
        ## Statistical Method
        
        Z-score measures how many standard deviations a value is from the mean:
        - |z| ≤ 2: Normal value (within 95% of data)
        - |z| > 2: Outlier (beyond 95% of data)
        
        ## Example
        
        **Input:**
        ```json
        [
          {"id": 1, "value": 10},
          {"id": 2, "value": 20},
          {"id": 3, "value": 100}
        ]
        ```
        
        **Properties:**
        ```json
        {
          "valueCol": "value"
        }
        ```
        
        **Statistics:**
        - Mean: 43.33
        - Std Dev: 40.28
        - Z-scores: -0.83, -0.58, 1.41
        
        **Output:**
        ```json
        [
          {"id": 1, "value": 10, "is_outlier": false},
          {"id": 2, "value": 20, "is_outlier": false},
          {"id": 3, "value": 100, "is_outlier": false}
        ]
        ```
        
        In this example, even 100 is not flagged because |z| = 1.41 < 2.0.
    """.trimIndent()

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "valueCol": {
              "type": "string",
              "title": "Value Column",
              "description": "The numeric column to analyze for outliers"
            }
          },
          "required": ["valueCol"]
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
              "scope": "#/properties/valueCol"
            }
          ]
        }
        """.trimIndent(),
        object: TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Detects outliers using Z-score method (flags values with |Z| > 2)",
        title = "Anomaly Detector (Z-Score)",
        subTitle = "Statistical outlier detection",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5M9 11.25v1.5M12 9v3.75m3-6v6" />
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "valueCol" to "value"
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val valueCol = properties?.get("valueCol") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "valueCol is not provided"
        )
        
        LOGGER.info("Detecting anomalies in column: $valueCol using Z-score method")
        
        // First pass: collect all values and rows
        val allRows = mutableListOf<Map<String, Any>>()
        val values = mutableListOf<Double>()
        
        inputs["data"]?.use { reader ->
            var row = reader.read()
            
            while (row != null) {
                allRows.add(row)
                val value = (row[valueCol] as? Number)?.toDouble() ?: 0.0
                values.add(value)
                row = reader.read()
            }
        }
        
        if (values.isEmpty()) {
            LOGGER.warn("No data to analyze")
            return
        }
        
        // Calculate mean and standard deviation
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        val std = sqrt(variance)
        
        LOGGER.info("Statistics: mean=$mean, std=$std, count=${values.size}")
        
        if (std == 0.0) {
            LOGGER.warn("Standard deviation is 0, all values are the same - no outliers detected")
            // Write all rows with is_outlier = false
            nodeOutputWriter.createOutputPortWriter("data").use { writer ->
                allRows.forEachIndexed { index, row ->
                    writer.write(index.toLong(), row + mapOf("is_outlier" to false))
                }
            }
            return
        }
        
        // Second pass: flag outliers
        var outlierCount = 0
        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
            allRows.forEachIndexed { index, row ->
                val value = (row[valueCol] as? Number)?.toDouble() ?: 0.0
                val zScore = (value - mean) / std
                val isOutlier = kotlin.math.abs(zScore) > Z_THRESHOLD
                
                if (isOutlier) {
                    outlierCount++
                    LOGGER.debug("Outlier detected: value=$value, z-score=$zScore")
                }
                
                writer.write(index.toLong(), row + mapOf("is_outlier" to isOutlier))
            }
        }
        
        LOGGER.info("Detected $outlierCount outliers out of ${allRows.size} rows")
    }
}
