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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Component
class TimeWindowAggregatorNodeModel: ProcessNodeModel() {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TimeWindowAggregatorNodeModel::class.java)
        private val objectMapper = ObjectMapper()
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    final override val inputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "input table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    final override val outputPorts = mapOf(
        "data" to ContinuumWorkflowModel.NodePort(
            name = "aggregated table",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    override val categories = listOf(
        "Aggregation & Time Series"
    )

    val propertiesSchema: Map<String, Any> = objectMapper.readValue(
        """
        {
          "type": "object",
          "properties": {
            "timeCol": {
              "type": "string",
              "title": "Time Column",
              "description": "The column containing timestamp values"
            },
            "valueCol": {
              "type": "string",
              "title": "Value Column",
              "description": "The column to aggregate (sum)"
            },
            "windowSize": {
              "type": "number",
              "title": "Window Size (minutes)",
              "description": "Size of time window in minutes",
              "minimum": 1
            }
          },
          "required": ["timeCol", "valueCol", "windowSize"]
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
              "scope": "#/properties/timeCol"
            },
            {
              "type": "Control",
              "scope": "#/properties/valueCol"
            },
            {
              "type": "Control",
              "scope": "#/properties/windowSize"
            }
          ]
        }
        """.trimIndent(),
        object: TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Aggregates values into time windows, summing by window buckets",
        title = "Time Window Aggregator",
        subTitle = "Group and sum by time windows",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "timeCol" to "time",
            "valueCol" to "value",
            "windowSize" to 5
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    private fun floorToWindow(dateTime: LocalDateTime, windowMinutes: Long): LocalDateTime {
        val minutesSinceEpoch = dateTime.until(LocalDateTime.of(1970, 1, 1, 0, 0), ChronoUnit.MINUTES)
        val bucketMinutes = (minutesSinceEpoch / windowMinutes) * windowMinutes
        return LocalDateTime.of(1970, 1, 1, 0, 0).plusMinutes(-bucketMinutes)
    }

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val timeCol = properties?.get("timeCol") as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "timeCol is not provided"
        )
        val valueCol = properties["valueCol"] as String? ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "valueCol is not provided"
        )
        val windowSize = (properties["windowSize"] as? Number)?.toLong() ?: throw NodeRuntimeException(
            workflowId = "",
            nodeId = "",
            message = "windowSize is not provided"
        )
        
        LOGGER.info("Aggregating time windows: timeCol=$timeCol, valueCol=$valueCol, windowSize=$windowSize minutes")
        
        // Read all rows and aggregate by window
        val buckets = mutableMapOf<String, Double>()
        var rowCount = 0
        
        inputs["data"]?.use { reader ->
            var row = reader.read()
            
            while (row != null) {
                try {
                    val timeStr = row[timeCol]?.toString() ?: ""
                    val value = (row[valueCol] as? Number)?.toDouble() ?: 0.0
                    
                    if (timeStr.isNotEmpty()) {
                        val dateTime = LocalDateTime.parse(timeStr, dateTimeFormatter)
                        val windowStart = floorToWindow(dateTime, windowSize)
                        val windowKey = windowStart.format(dateTimeFormatter)
                        
                        buckets[windowKey] = buckets.getOrDefault(windowKey, 0.0) + value
                        rowCount++
                    }
                } catch (e: Exception) {
                    LOGGER.warn("Failed to parse row: ${e.message}")
                }
                
                row = reader.read()
            }
        }
        
        LOGGER.info("Processed $rowCount rows into ${buckets.size} time windows")
        
        // Write aggregated results
        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
            var rowNumber = 0L
            
            buckets.entries.sortedBy { it.key }.forEach { (windowStart, sumValue) ->
                writer.write(rowNumber, mapOf(
                    "window_start" to windowStart,
                    "sum_value" to sumValue
                ))
                rowNumber++
            }
            
            LOGGER.info("Output $rowNumber aggregated windows")
        }
    }
}
