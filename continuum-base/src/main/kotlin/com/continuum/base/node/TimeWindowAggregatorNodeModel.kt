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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Time Window Aggregator Node Model
 *
 * Aggregates time-series data into fixed-duration time windows by summing values.
 * Each time window is defined by a start time and includes all data points that fall
 * within the window duration.
 *
 * **Input Ports:**
 * - `data`: Input table containing time-series data with timestamp and value columns
 *
 * **Output Ports:**
 * - `data`: Aggregated table with one row per time window containing window start and sum
 *
 * **Configuration Properties:**
 * - `timeCol` (required): Name of the column containing timestamp strings
 *   - Format: "yyyy-MM-dd HH:mm:ss" (e.g., "2026-02-21 14:30:45")
 * - `valueCol` (required): Name of the numeric column to aggregate (sum)
 * - `windowSize` (required): Duration of each time window in minutes (must be >= 1)
 *
 * **Behavior:**
 * - Reads all input rows into memory (required for windowing)
 * - Groups rows by time window based on timestamp column
 * - Time windows are aligned to hour boundaries within the minute
 *   - Example with 5-minute windows: 14:00, 14:05, 14:10, 14:15, etc.
 * - Sums values in each window
 * - Outputs windows sorted by start time
 * - Rows with invalid timestamps are skipped with a warning
 * - Empty input produces empty output
 *
 * **Window Calculation:**
 * Windows are floored to the nearest window boundary within the hour.
 * For a 5-minute window:
 * - 14:03:45 → window starts at 14:00:00
 * - 14:07:12 → window starts at 14:05:00
 * - 14:59:59 → window starts at 14:55:00
 *
 * **Example:**
 * ```
 * Input:
 * [
 *   {time: "2026-02-21 14:03:00", value: 10},
 *   {time: "2026-02-21 14:04:00", value: 15},
 *   {time: "2026-02-21 14:07:00", value: 20}
 * ]
 * timeCol: "time"
 * valueCol: "value"
 * windowSize: 5
 *
 * Output:
 * [
 *   {window_start: "2026-02-21 14:00:00", sum_value: 25.0},  // 10 + 15
 *   {window_start: "2026-02-21 14:05:00", sum_value: 20.0}   // 20
 * ]
 * ```
 *
 * **Limitations:**
 * - Window size is limited to within-hour boundaries (max 59 minutes)
 * - Does not handle timezone conversions (uses LocalDateTime)
 * - Requires all data in memory (not suitable for unbounded streams)
 *
 * @since 1.0
 * @see ProcessNodeModel
 * @see LocalDateTime
 */
@Component
class TimeWindowAggregatorNodeModel : ProcessNodeModel() {
  companion object {
    private val LOGGER = LoggerFactory.getLogger(TimeWindowAggregatorNodeModel::class.java)
    private val objectMapper = ObjectMapper()
    // Date format for parsing and output: "2026-02-21 14:30:45"
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
    object : TypeReference<Map<String, Any>>() {}
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
    object : TypeReference<Map<String, Any>>() {}
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

  /**
   * Floors a timestamp to the start of its time window.
   *
   * Calculates which time window a given timestamp belongs to by rounding down
   * the minutes to the nearest window boundary. The window boundaries are aligned
   * within the hour (not across days or hours).
   *
   * **Algorithm:**
   * 1. Extract the minute component (0-59)
   * 2. Divide by window size and truncate (integer division)
   * 3. Multiply back to get the floored minute
   * 4. Set minute to floored value, seconds and nanoseconds to 0
   *
   * **Examples** (with 5-minute windows):
   * - 14:03:45 → 14:00:00 (3 / 5 = 0, 0 * 5 = 0)
   * - 14:07:12 → 14:05:00 (7 / 5 = 1, 1 * 5 = 5)
   * - 14:59:59 → 14:55:00 (59 / 5 = 11, 11 * 5 = 55)
   *
   * **Note:** This implementation only works within hour boundaries. For window sizes
   * larger than 60 minutes or cross-hour windowing, a different algorithm is needed.
   *
   * @param dateTime The timestamp to floor
   * @param windowMinutes Size of the time window in minutes
   * @return New LocalDateTime at the start of the containing window
   */
  private fun floorToWindow(dateTime: LocalDateTime, windowMinutes: Long): LocalDateTime {
    val minute = dateTime.minute.toLong()
    val flooredMinute = (minute / windowMinutes) * windowMinutes
    return dateTime
      .withMinute(flooredMinute.toInt())
      .withSecond(0)
      .withNano(0)
  }

  /**
   * Executes the time window aggregation.
   *
   * Reads all input rows, groups them by time window based on the timestamp column,
   * sums values within each window, and outputs one row per window with the window
   * start time and aggregated sum.
   *
   * @param properties Configuration map containing:
   *   - `timeCol` (String, required): Name of column with timestamps ("yyyy-MM-dd HH:mm:ss")
   *   - `valueCol` (String, required): Name of numeric column to sum
   *   - `windowSize` (Number, required): Window duration in minutes (>= 1)
   * @param inputs Map of input port readers, expects "data" port
   * @param nodeOutputWriter Writer for output port data
   *
   * @throws NodeRuntimeException if any required property is missing or invalid
   *
   * @see NodeInputReader
   * @see NodeOutputWriter
   * @see LocalDateTime
   */
  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback
  ) {
    // === Validate and extract required properties ===
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

    // === Read all rows and aggregate by window (requires full dataset in memory) ===
    // Map from window start time string to sum of values in that window
    val buckets = mutableMapOf<String, Double>()
    var rowCount = 0

    inputs["data"]?.use { reader ->
      var row = reader.read()

      // Process each row, parse timestamp, assign to window, and accumulate value
      while (row != null) {
        try {
          // Extract timestamp string and value from row
          val timeStr = row[timeCol]?.toString() ?: ""
          val value = (row[valueCol] as? Number)?.toDouble() ?: 0.0

          if (timeStr.isNotEmpty()) {
            // Parse timestamp using configured formatter
            val dateTime = LocalDateTime.parse(timeStr, dateTimeFormatter)

            // Calculate which window this timestamp belongs to
            val windowStart = floorToWindow(dateTime, windowSize)

            // Format window start back to string for use as map key
            val windowKey = windowStart.format(dateTimeFormatter)

            // Accumulate value into the appropriate window bucket
            buckets[windowKey] = buckets.getOrDefault(windowKey, 0.0) + value
            rowCount++
          }
        } catch (e: Exception) {
          // Skip rows with invalid timestamps or parse errors
          LOGGER.warn("Failed to parse row: ${e.message}")
        }

        row = reader.read()
      }
    }

    LOGGER.info("Processed $rowCount rows into ${buckets.size} time windows")

    // === Write aggregated results sorted by window start time ===
    nodeOutputWriter.createOutputPortWriter("data").use { writer ->
      var rowNumber = 0L

      // Sort by window start time (lexicographic sort works due to ISO format)
      buckets.entries.sortedBy { it.key }.forEach { (windowStart, sumValue) ->
        writer.write(
          rowNumber, mapOf(
            "window_start" to windowStart,  // Window start timestamp
            "sum_value" to sumValue          // Sum of all values in this window
          )
        )
        rowNumber++
      }

      LOGGER.info("Output $rowNumber aggregated windows")
    }
  }
}
