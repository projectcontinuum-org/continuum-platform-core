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

/**
 * Dynamic Row Filter Node Model
 *
 * Filters rows from an input table based on a dynamic threshold comparison.
 * Only rows where the specified column value is strictly greater than the threshold are passed through.
 *
 * **Input Ports:**
 * - `data`: Input table containing rows to be filtered
 *
 * **Output Ports:**
 * - `data`: Filtered table containing only rows that pass the threshold condition
 *
 * **Configuration Properties:**
 * - `columnName` (required): The name of the numeric column to compare against the threshold
 * - `threshold` (required): The numeric threshold value. Rows with column value > threshold pass through
 *
 * **Behavior:**
 * - Uses strictly greater than comparison (>), not greater than or equal (>=)
 * - Missing or non-numeric column values default to 0.0 with a warning logged
 * - Row indices in output are sequential starting from 0, not preserving original indices
 * - Empty input produces empty output
 *
 * **Example:**
 * ```
 * Input: [{age: 35}, {age: 25}, {age: 40}]
 * columnName: "age"
 * threshold: 30
 * Output: [{age: 35}, {age: 40}]  // Only rows where age > 30
 * ```
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@Component
class DynamicRowFilterNodeModel : ProcessNodeModel() {
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
    object : TypeReference<Map<String, Any>>() {}
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
    object : TypeReference<Map<String, Any>>() {}
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

  /**
   * Executes the row filtering operation.
   *
   * Reads rows from the input stream, compares the specified column value against the threshold,
   * and writes only rows that pass the filter (value > threshold) to the output stream.
   *
   * @param properties Configuration map containing:
   *   - `columnName` (String, required): Name of the numeric column to filter on
   *   - `threshold` (Number, required): Threshold value for comparison
   * @param inputs Map of input port readers, expects "data" port with table rows
   * @param nodeOutputWriter Writer for output port data
   *
   * @throws NodeRuntimeException if columnName or threshold properties are missing or invalid
   *
   * @see NodeInputReader
   * @see NodeOutputWriter
   */
  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter
  ) {
    // === Validate and extract required properties ===
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

    // === Create output writer and process input stream ===
    nodeOutputWriter.createOutputPortWriter("data").use { writer ->
      inputs["data"]?.use { reader ->
        var row = reader.read()
        var rowNumber = 0L // Sequential row index for output (only counts filtered rows)

        // === Read and filter rows one at a time ===
        while (row != null) {
          // Extract numeric value from specified column
          // If column is missing or not numeric, default to 0.0 with warning
          val value = (row[columnName] as? Number)?.toDouble() ?: run {
            if (!row.containsKey(columnName)) {
              LOGGER.warn("Column '$columnName' not found in row, defaulting to 0.0")
            }
            0.0
          }

          // === Apply filter: only pass rows where value > threshold (strictly greater than) ===
          if (value > threshold) {
            writer.write(rowNumber, row)
            rowNumber++ // Increment only for rows that pass the filter
          }

          // Read next row from input stream
          row = reader.read()
        }

        LOGGER.info("Filtered to $rowNumber rows")
      }
    }
  }
}
