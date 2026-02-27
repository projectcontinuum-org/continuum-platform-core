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
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component

/**
 * Conditional Splitter Node Model
 *
 * Splits input rows into two output streams based on a numeric threshold comparison.
 * Each row is routed to either the "high" or "low" output port based on whether the
 * specified column value is greater than or equal to the threshold.
 *
 * **Input Ports:**
 * - `data`: Input table containing rows to be split
 *
 * **Output Ports:**
 * - `high`: Rows where column value >= threshold
 * - `low`: Rows where column value < threshold
 *
 * **Configuration Properties:**
 * - `column` (required): Name of the numeric column to compare
 * - `threshold` (required): Numeric threshold value for splitting
 *
 * **Behavior:**
 * - Uses greater than or equal (>=) comparison for "high" output
 * - Missing or non-numeric column values default to 0.0 with a warning logged
 * - Each output stream maintains its own sequential row numbering starting from 0
 * - All original row data is preserved in both outputs
 * - Empty input results in empty outputs on both ports
 *
 * **Example:**
 * ```
 * Input: [{value: 100}, {value: 25}, {value: 50}]
 * column: "value"
 * threshold: 50
 * High output: [{value: 100}, {value: 50}]  // >= 50
 * Low output:  [{value: 25}]                // < 50
 * ```
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@Component
class ConditionalSplitterNodeModel : ProcessNodeModel() {
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
    object : TypeReference<Map<String, Any>>() {}
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
    object : TypeReference<Map<String, Any>>() {}
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

  /**
   * Executes the conditional split operation.
   *
   * Reads rows from input stream and routes each row to either "high" or "low" output
   * based on whether the specified column value meets the threshold criterion.
   * Both output streams maintain independent sequential row numbering.
   *
   * @param properties Configuration map containing:
   *   - `column` (String, required): Name of the numeric column to evaluate
   *   - `threshold` (Number, required): Threshold value for splitting decision
   * @param inputs Map of input port readers, expects "data" port
   * @param nodeOutputWriter Writer for output port data
   *
   * @throws NodeRuntimeException if column or threshold properties are missing or invalid
   *
   * @see NodeInputReader
   * @see NodeOutputWriter
   */
  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback
  ) {
    // === Validate and extract required properties ===
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

    // Track row counts for each output stream independently
    var highCount = 0L
    var lowCount = 0L

    // === Create both output writers upfront (required for proper resource management) ===
    nodeOutputWriter.createOutputPortWriter("high").use { highWriter ->
      nodeOutputWriter.createOutputPortWriter("low").use { lowWriter ->
        inputs["data"]?.use { reader ->
          var row = reader.read()

          // === Process each row and route to appropriate output ===
          while (row != null) {
            // Extract numeric value from specified column
            // If column is missing or not numeric, default to 0.0 with warning
            val value = (row[column] as? Number)?.toDouble() ?: run {
              if (!row.containsKey(column)) {
                LOGGER.warn("Column '$column' not found in row, defaulting to 0.0")
              }
              0.0
            }

            // Route row based on threshold comparison
            // High output: value >= threshold (greater than or equal)
            // Low output: value < threshold (less than)
            if (value >= threshold) {
              highWriter.write(highCount, row)
              highCount++
            } else {
              lowWriter.write(lowCount, row)
              lowCount++
            }

            // Read next row from input stream
            row = reader.read()
          }
        }
      }
    }

    LOGGER.info("Split complete: $highCount rows to 'high', $lowCount rows to 'low'")
  }
}
