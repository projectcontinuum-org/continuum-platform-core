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
 * Joint Node Model
 *
 * Joins (concatenates) column values from two input streams into a single output column.
 * Processes rows pair-wise from both inputs, combining specified columns with a space separator.
 *
 * **Input Ports:**
 * - `input-1`: First input stream
 * - `input-2`: Second input stream
 *
 * **Output Ports:**
 * - `output-1`: Stream with joined column values
 *
 * **Configuration Properties:**
 * - `inputs` (required): Array of input configurations, must contain at least 2 elements:
 *   - Each element has `columnName` property specifying which column to extract
 * - `outputsColumnName` (required): Name for the output column containing joined values
 *
 * **Behavior:**
 * - Requires exactly 2 input column configurations (validates at runtime)
 * - Reads one row from each input stream simultaneously
 * - Concatenates values from specified columns with a space separator
 * - Automatically converts all data types to strings (numbers, booleans, etc.)
 * - Missing column values are treated as empty strings
 * - Stops when either input stream is exhausted
 * - Output contains only the joined column (original columns are not preserved)
 *
 * **Example:**
 * ```
 * Input-1: [{msg-1: "Hello"}]
 * Input-2: [{msg-2: "World"}]
 * inputs: [{columnName: "msg-1"}, {columnName: "msg-2"}]
 * outputsColumnName: "message"
 * Output: [{message: "Hello World"}]
 * ```
 *
 * @since 1.0
 * @see ProcessNodeModel
 */
@Component
class JointNodeModel : ProcessNodeModel() {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(JointNodeModel::class.java)
    private val objectMapper = ObjectMapper()
  }

  final override val inputPorts = mapOf(
    "input-1" to ContinuumWorkflowModel.NodePort(
      name = "first input string",
      contentType = TEXT_PLAIN_VALUE
    ),
    "input-2" to ContinuumWorkflowModel.NodePort(
      name = "second input string",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  final override val outputPorts = mapOf(
    "output-1" to ContinuumWorkflowModel.NodePort(
      name = "part 1",
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
            "inputs": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "columnName": {
                            "type": "string",
                            "title": "Input column Name"
                        }
                    },
                    "required": ["columnName"]
                }
            },
            "outputsColumnName": {
              "type": "string",
              "title": "Output Column Name",
              "description": "The name of the column to split"
            }
          },
          "required": ["outputsColumnName", "inputs"]
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
              "scope": "#/properties/inputs",
              "options": {
                "detail": {
                  "type": "VerticalLayout",
                  "elements": [
                    {
                      "type": "Control",
                      "scope": "#/properties/columnName"
                    }
                  ]
                }
              }
            },
            {
              "type": "Control",
              "scope": "#/properties/outputsColumnName"
            }
          ]
        }
        """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  override val metadata = ContinuumWorkflowModel.NodeData(
    id = this.javaClass.name,
    description = "Joint the input strings into one",
    title = "Joint Node",
    subTitle = "Joint the input strings",
    nodeModel = this.javaClass.name,
    icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path d="M7 7V1.414a1 1 0 0 1 2 0V2h5a1 1 0 0 1 .8.4l.975 1.3a.5.5 0 0 1 0 .6L14.8 5.6a1 1 0 0 1-.8.4H9v10H7v-5H2a1 1 0 0 1-.8-.4L.225 9.3a.5.5 0 0 1 0-.6L1.2 7.4A1 1 0 0 1 2 7zm1 3V8H2l-.75 1L2 10zm0-5h6l.75-1L14 3H8z"/>
            </svg>
        """.trimIndent(),
    inputs = inputPorts,
    outputs = outputPorts,
    properties = mapOf(
      "inputs" to listOf(
        mapOf("columnName" to "msg-1"),
        mapOf("columnName" to "msg-2")
      ),
      "outputsColumnName" to "message"
    ),
    propertiesSchema = propertiesSchema,
    propertiesUISchema = propertiesUiSchema
  )

  /**
   * Executes the column join operation for two input streams.
   *
   * Validates input configuration, reads rows pair-wise from both input streams,
   * extracts and concatenates specified column values, and writes to output stream.
   * Processing continues until either input stream is exhausted.
   *
   * @param properties Configuration map containing:
   *   - `inputs` (List<Map<String, String>>, required): Array of input configurations
   *     Must contain at least 2 elements, each with a `columnName` property
   *   - `outputsColumnName` (String, optional): Name for output column (defaults to "message")
   * @param inputs Map of input port readers, expects "input-1" and "input-2" ports
   * @param nodeOutputWriter Writer for output port data
   *
   * @throws NodeRuntimeException if:
   *   - `inputs` property is missing or not a list
   *   - `inputs` list has fewer than 2 elements
   *   - Any input element is missing `columnName` property
   *
   * @see NodeInputReader
   * @see NodeOutputWriter
   */
  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter
  ) {
    // === Validate and extract input column configurations ===
    // Cast to List<Map> with type safety check
    val inputColumnNames = (properties?.get("inputs") as? List<Map<String, String>>)
      ?: throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "inputs is not provided"
      )

    // Validate array has at least 2 elements (required for joining)
    if (inputColumnNames.size < 2) {
      throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "inputs must contain at least 2 elements, got: ${inputColumnNames.size}"
      )
    }

    // Extract column names from first two input configurations
    val inputColumnName1 = inputColumnNames[0]["columnName"] ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "Input column name 1 is not provided"
    )
    val inputColumnName2 = inputColumnNames[1]["columnName"] ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "Input column name 2 is not provided"
    )

    // Output column name defaults to "message" if not provided
    val outputColumnName = properties["outputsColumnName"] as String? ?: "message"

    LOGGER.info("Jointing the input: ${objectMapper.writeValueAsString(inputs)}")

    // === Create output writer and open both input streams ===
    nodeOutputWriter.createOutputPortWriter("output-1").use { writer ->
      inputs["input-1"]?.use { reader1 ->
        inputs["input-2"]?.use { reader2 ->
          var input1 = reader1.read()
          var input2 = reader2.read()
          var rowNumber = 0L

          // === Process rows pair-wise until either stream is exhausted ===
          while (input1 != null && input2 != null) {
            // Extract values from specified columns, converting to string
            // Missing columns or null values become empty strings
            val value1 = input1[inputColumnName1]?.toString() ?: ""
            val value2 = input2[inputColumnName2]?.toString() ?: ""

            // Concatenate with space separator
            val dataCells = "$value1 $value2"

            // Write output row with only the joined column
            writer.write(
              rowNumber, mapOf(
                outputColumnName to dataCells
              )
            )

            // Read next row from each input stream
            input1 = reader1.read()
            input2 = reader2.read()
            rowNumber++
          }
        }
      }
    }

  }

}