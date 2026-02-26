package com.continuum.base.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.TriggerNodeModel
import com.continuum.core.commons.utils.NodeOutputWriter
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Time Trigger Node Model
 *
 * A workflow trigger node that generates rows with current timestamps.
 * Typically used as the starting point of a workflow to initiate data processing.
 *
 * **Input Ports:**
 * - None (this is a trigger node)
 *
 * **Output Ports:**
 * - `output-1`: Stream of rows containing timestamp messages
 *
 * **Configuration Properties:**
 * - `message` (optional): Prefix text for each output message (defaults to "Logging at")
 * - `rowCount` (optional): Number of rows to generate (defaults to 10)
 *   - Must be a positive integer (> 0)
 *   - Invalid values (negative, zero, non-numeric) default to 10
 *   - Accepts both Number and String types
 *
 * **Behavior:**
 * - Generates the specified number of rows on execution
 * - Each row contains a single "message" column with format: "{message prefix} {ISO-8601 timestamp}"
 * - Timestamps are generated at the time each row is written (may have slight variations)
 * - Row indices are sequential starting from 0
 *
 * **Example:**
 * ```
 * message: "Event at"
 * rowCount: 3
 * Output:
 * [
 *   {message: "Event at 2026-02-21T10:30:15.123Z"},
 *   {message: "Event at 2026-02-21T10:30:15.124Z"},
 *   {message: "Event at 2026-02-21T10:30:15.125Z"}
 * ]
 * ```
 *
 * **Use Cases:**
 * - Workflow testing and debugging
 * - Scheduled job initialization
 * - Time-series data generation
 * - Load testing with configurable row counts
 *
 * @since 1.0
 * @see TriggerNodeModel
 */
@Component
class TimeTriggerNodeModel : TriggerNodeModel() {
  override val categories = listOf(
    "Trigger"
  )

  final override val outputPorts = mapOf(
    "output-1" to ContinuumWorkflowModel.NodePort(
      name = "output-1",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  override val metadata = ContinuumWorkflowModel.NodeData(
    id = this.javaClass.name,
    description = "Starts the workflow execution with the current time as the output",
    title = "Start Node",
    subTitle = "Starts the workflow execution",
    nodeModel = this.javaClass.name,
    icon = "mui/Bolt",
    outputs = outputPorts,
    properties = mapOf(
      "message" to "Logging at",
      "rowCount" to 10
    ),
    propertiesSchema = mapOf(
      "type" to "object",
      "properties" to mapOf(
        "message" to mapOf(
          "type" to "string"
        ),
        "rowCount" to mapOf(
          "type" to "number",
          "minimum" to 1,
        )
      ),
      "required" to listOf(
        "message",
        "rowCount"
      )
    ),
    propertiesUISchema = mapOf(
      "type" to "VerticalLayout",
      "elements" to listOf(
        mapOf(
          "type" to "Control",
          "scope" to "#/properties/message"
        ),
        mapOf(
          "type" to "Control",
          "scope" to "#/properties/rowCount"
        )
      )
    )
  )

  /**
   * Executes the trigger node to generate rows with timestamps.
   *
   * Validates the rowCount property (defaults to 10 if invalid), then generates
   * the specified number of rows, each containing a message with the current timestamp.
   *
   * @param properties Configuration map containing:
   *   - `message` (String, optional): Prefix for timestamp messages (defaults to "Logging at")
   *   - `rowCount` (Number|String, optional): Number of rows to generate (defaults to 10)
   *     - Accepts Number types (Int, Long, Double)
   *     - Also accepts String that can be parsed to Long
   *     - Values <= 0 are rejected and default to 10
   * @param nodeOutputWriter Writer for output port data
   *
   * @see NodeOutputWriter
   * @see Instant
   */
  override fun execute(
    properties: Map<String, Any>?,
    nodeOutputWriter: NodeOutputWriter
  ) {
    // === Validate and extract rowCount with multiple type support and fallback to 10 ===
    // Try as Number first (Int, Long, Double), then try parsing String, finally default to 10
    // Only accept positive values (> 0), reject zero and negative
    val rowCount = (properties?.get("rowCount") as? Number)?.toLong()
      ?.takeIf { it > 0 }  // Accept only positive numbers
      ?: properties?.get("rowCount")?.toString()?.toLongOrNull()?.takeIf { it > 0 }  // Try String parsing
      ?: 10L  // Default to 10 if property is missing, invalid, or <= 0

    // Message prefix defaults to "Logging at" if not provided
    val message = properties?.get("message") as? String ?: "Logging at"

    // === Generate specified number of rows with current timestamps ===
    nodeOutputWriter.createOutputPortWriter("output-1").use {
      for (i in 0 until rowCount) {
        // Write row with sequential index and timestamped message
        // Timestamp is captured at write time (may vary slightly between rows)
        it.write(
          i, mapOf(
            "message" to "$message ${Instant.now()}"  // ISO-8601 format
          )
        )
      }
    }
  }
}