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
 * Column Join Node Model
 *
 * Joins columns from two input tables by concatenating their values with a space separator.
 * Processes rows pair-wise from left and right tables, stopping when either table runs out of rows.
 *
 * **Input Ports:**
 * - `left`: Left input table
 * - `right`: Right input table
 *
 * **Output Ports:**
 * - `output`: Table with joined column values
 *
 * **Configuration Properties:**
 * - `columnNameLeft` (required): Column name from the left table to join
 * - `columnNameRight` (required): Column name from the right table to join
 * - `outputColumnName` (required): Name for the output column containing joined values
 *
 * **Behavior:**
 * - Reads one row from each table simultaneously
 * - Concatenates values from specified columns with a space separator
 * - Automatically converts all data types to strings (numbers, booleans, etc.)
 * - Missing column values are treated as empty strings
 * - Stops when either input table is exhausted (similar to SQL inner join on row number)
 * - Trailing/leading whitespace is trimmed from final result
 *
 * **Example:**
 * ```
 * Left table:  [{name: "Alice"}, {name: "Bob"}]
 * Right table: [{city: "NYC"}, {city: "LA"}]
 * columnNameLeft: "name"
 * columnNameRight: "city"
 * outputColumnName: "fullInfo"
 * Output: [{fullInfo: "Alice NYC"}, {fullInfo: "Bob LA"}]
 * ```
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@Component
class ColumnJoinNodeModel : ProcessNodeModel() {
  companion object {
    private val LOGGER = LoggerFactory.getLogger(ColumnJoinNodeModel::class.java)
    private val objectMapper = ObjectMapper()
  }

  final override val inputPorts = mapOf(
    "left" to ContinuumWorkflowModel.NodePort(
      name = "left input table",
      contentType = TEXT_PLAIN_VALUE
    ),
    "right" to ContinuumWorkflowModel.NodePort(
      name = "right input table",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  final override val outputPorts = mapOf(
    "output" to ContinuumWorkflowModel.NodePort(
      name = "output table",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  override val categories = listOf(
    "Processing"
  )

  val propertiesSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "object",
          "properties": {
            "columnNameLeft": {
              "type": "string",
              "title": "Left Column Name",
              "description": "The column name from the left table"
            },
            "columnNameRight": {
              "type": "string",
              "title": "Right Column Name",
              "description": "The column name from the right table"
            },
            "outputColumnName": {
              "type": "string",
              "title": "Output Column Name",
              "description": "The name of the joined output column"
            }
          },
          "required": ["columnNameLeft", "columnNameRight", "outputColumnName"]
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
              "scope": "#/properties/columnNameLeft"
            },
            {
              "type": "Control",
              "scope": "#/properties/columnNameRight"
            },
            {
              "type": "Control",
              "scope": "#/properties/outputColumnName"
            }
          ]
        }
        """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  override val metadata = ContinuumWorkflowModel.NodeData(
    id = this.javaClass.name,
    description = "Joins two columns from left and right tables into one output column",
    title = "Column Join Node",
    subTitle = "Join columns from two tables",
    nodeModel = this.javaClass.name,
    icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path d="M7 7V1.414a1 1 0 0 1 2 0V2h5a1 1 0 0 1 .8.4l.975 1.3a.5.5 0 0 1 0 .6L14.8 5.6a1 1 0 0 1-.8.4H9v10H7v-5H2a1 1 0 0 1-.8-.4L.225 9.3a.5.5 0 0 1 0-.6L1.2 7.4A1 1 0 0 1 2 7zm1 3V8H2l-.75 1L2 10zm0-5h6l.75-1L14 3H8z"/>
            </svg>
        """.trimIndent(),
    inputs = inputPorts,
    outputs = outputPorts,
    properties = mapOf(
      "columnNameLeft" to "name",
      "columnNameRight" to "city",
      "outputColumnName" to "fullInfo"
    ),
    propertiesSchema = propertiesSchema,
    propertiesUISchema = propertiesUiSchema
  )

  /**
   * Executes the column join operation.
   *
   * Reads rows pair-wise from left and right input tables, extracts values from specified columns,
   * concatenates them with a space separator, and writes the result to the output table.
   * Processing continues until either input table is exhausted.
   *
   * @param properties Configuration map containing:
   *   - `columnNameLeft` (String, required): Column name from left table
   *   - `columnNameRight` (String, required): Column name from right table
   *   - `outputColumnName` (String, required): Name for output column with joined values
   * @param inputs Map of input port readers, expects "left" and "right" ports
   * @param nodeOutputWriter Writer for output port data
   *
   * @throws NodeRuntimeException if any required property is missing
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
    val columnNameLeft = properties?.get("columnNameLeft") as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "columnNameLeft is not provided"
    )
    val columnNameRight = properties["columnNameRight"] as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "columnNameRight is not provided"
    )
    val outputColumnName = properties["outputColumnName"] as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "outputColumnName is not provided"
    )

    LOGGER.info("Joining columns: $columnNameLeft and $columnNameRight into $outputColumnName")

    // === Create output writer and open both input streams ===
    nodeOutputWriter.createOutputPortWriter("output").use { writer ->
      inputs["left"]?.use { leftReader ->
        inputs["right"]?.use { rightReader ->
          var leftRow = leftReader.read()
          var rightRow = rightReader.read()
          var rowNumber = 0L

          // === Process rows pair-wise until either stream is exhausted ===
          while (leftRow != null && rightRow != null) {
            // Extract values from specified columns, converting to string
            // Missing columns or null values become empty strings
            val leftValue = leftRow[columnNameLeft]?.toString() ?: ""
            val rightValue = rightRow[columnNameRight]?.toString() ?: ""

            // Concatenate with space and trim whitespace
            val joinedValue = "$leftValue $rightValue".trim()

            // Write output row with only the joined column
            writer.write(
              rowNumber, mapOf(
                outputColumnName to joinedValue
              )
            )

            // Read next row from each input stream
            leftRow = leftReader.read()
            rightRow = rightReader.read()
            rowNumber++
          }
        }
      }
    }
  }
}
