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

/**
 * Node model for detecting anomalies in numeric data using the Z-score statistical method.
 *
 * This node analyzes a specified numeric column in the input table and flags rows as outliers
 * based on their Z-score (standard score). The Z-score measures how many standard deviations
 * a value is from the mean.
 *
 * **Algorithm**: Z-score method
 * - Z = (value - mean) / standard_deviation
 * - Values with |Z| > threshold are flagged as outliers
 * - Default threshold is 2.0, which flags approximately 5% of data in a normal distribution
 *
 * **Memory Efficiency**:
 * This implementation uses a three-pass streaming algorithm to handle large datasets without
 * loading all rows into memory:
 * 1. First pass: Calculate mean
 * 2. Second pass: Calculate standard deviation
 * 3. Third pass: Flag outliers and write results
 *
 * **Input**:
 * - Port "data": Table with numeric column to analyze
 *
 * **Output**:
 * - Port "data": Original table with an additional boolean column indicating outliers
 *
 * **Configuration Properties**:
 * - valueCol (required): Name of the numeric column to analyze
 * - zThreshold (optional): Z-score threshold for outlier detection (default: 2.0)
 * - outputColumnName (optional): Name of the output boolean column (default: "is_outlier")
 *
 * **Use Cases**:
 * - Detecting anomalous sensor readings in IoT data
 * - Identifying unusual transaction amounts in financial data
 * - Finding outliers in scientific measurements
 * - Quality control in manufacturing data
 *
 * @see ProcessNodeModel
 * @author Continuum Workflow
 */
@Component
class AnomalyDetectorZScoreNodeModel : ProcessNodeModel() {
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

  val propertiesSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "object",
          "properties": {
            "valueCol": {
              "type": "string",
              "title": "Value Column",
              "description": "The numeric column to analyze for outliers"
            },
            "zThreshold": {
              "type": "number",
              "title": "Z-Score Threshold",
              "description": "Threshold for outlier detection (values with |Z| > threshold are flagged). Default: 2.0 (~95% coverage)",
              "default": 2.0,
              "minimum": 0.1,
              "maximum": 5.0
            },
            "outputColumnName": {
              "type": "string",
              "title": "Output Column Name",
              "description": "Name of the column to add with outlier flags",
              "default": "is_outlier"
            }
          },
          "required": ["valueCol"]
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
              "scope": "#/properties/valueCol"
            },
            {
              "type": "Control",
              "scope": "#/properties/zThreshold"
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
      "valueCol" to "value",
      "zThreshold" to 2.0,
      "outputColumnName" to "is_outlier"
    ),
    propertiesSchema = propertiesSchema,
    propertiesUISchema = propertiesUiSchema
  )

  /**
   * Executes the Z-score anomaly detection algorithm using a memory-efficient streaming approach.
   *
   * This implementation uses a three-pass streaming algorithm to avoid storing all rows in memory,
   * which is critical for handling large datasets:
   * 1. First pass: Calculate the mean by accumulating sum and count
   * 2. Second pass: Calculate standard deviation using the computed mean
   * 3. Third pass: Flag outliers and write output rows
   *
   * Z-score formula: Z = (value - mean) / std
   * A value is flagged as an outlier if |Z| > zThreshold
   *
   * @param properties Configuration containing valueCol, zThreshold, and outputColumnName
   * @param inputs Input data stream containing the table to analyze
   * @param nodeOutputWriter Writer for output data with outlier flags
   */
  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter
  ) {
    // Extract and validate configuration properties
    val valueCol = properties?.get("valueCol") as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "valueCol is not provided"
    )

    val zThreshold = (properties?.get("zThreshold") as? Number)?.toDouble() ?: Z_THRESHOLD
    val outputColumnName = properties?.get("outputColumnName") as? String ?: "is_outlier"

    LOGGER.info("Detecting anomalies in column: $valueCol using Z-score method (threshold: $zThreshold)")

    // ========================================
    // FIRST PASS: Calculate mean
    // ========================================
    // Stream through all rows once to calculate the mean without storing them in memory.
    // We only keep track of the running sum and count.
    var sum = 0.0
    var count = 0L
    var nullCount = 0

    inputs["data"]?.use { reader ->
      var row = reader.read()

      while (row != null) {
        val valueRaw = row[valueCol]

        // Handle null values and non-numeric data gracefully
        val value = when {
          valueRaw == null -> {
            nullCount++
            LOGGER.warn("Row $count: Column '$valueCol' is null, defaulting to 0.0")
            0.0
          }
          valueRaw is Number -> valueRaw.toDouble()
          else -> {
            nullCount++
            LOGGER.warn("Row $count: Column '$valueCol' value '$valueRaw' is not a number, defaulting to 0.0")
            0.0
          }
        }

        // Accumulate sum for mean calculation
        sum += value
        count++
        row = reader.read()
      }
    }

    if (nullCount > 0) {
      LOGGER.warn("Found $nullCount null or non-numeric values in column '$valueCol', defaulted to 0.0")
    }

    // Validate that we have data to analyze
    if (count == 0L) {
      LOGGER.warn("No data to analyze")
      return
    }

    // Calculate the mean: mean = sum / count
    val mean = sum / count

    // ========================================
    // SECOND PASS: Calculate standard deviation
    // ========================================
    // Stream through all rows again to calculate standard deviation.
    // We use the formula: std = sqrt(sum((x - mean)^2) / count)
    var sumSquaredDiff = 0.0
    var currentCount = 0L

    inputs["data"]?.use { reader ->
      var row = reader.read()

      while (row != null) {
        val valueRaw = row[valueCol]
        val value = (valueRaw as? Number)?.toDouble() ?: 0.0

        // Accumulate the sum of squared differences from the mean
        sumSquaredDiff += (value - mean).pow(2)
        currentCount++
        row = reader.read()
      }
    }

    // Calculate standard deviation: std = sqrt(variance)
    val std = sqrt(sumSquaredDiff / count)

    LOGGER.info("Statistics: mean=$mean, std=$std, count=$count")

    // Handle edge case where all values are identical (std = 0)
    if (std == 0.0) {
      LOGGER.warn("Standard deviation is 0, all values are the same - no outliers detected")

      // Stream through and mark all rows as non-outliers
      nodeOutputWriter.createOutputPortWriter("data").use { writer ->
        inputs["data"]?.use { reader ->
          var index = 0L
          var row = reader.read()

          while (row != null) {
            writer.write(index, row + mapOf(outputColumnName to false))
            index++
            row = reader.read()
          }
        }
      }
      return
    }

    // ========================================
    // THIRD PASS: Flag outliers and write output
    // ========================================
    // Stream through all rows one final time to calculate Z-scores and flag outliers.
    // Z-score = (value - mean) / std
    // A value is an outlier if |Z-score| > zThreshold
    var outlierCount = 0

    nodeOutputWriter.createOutputPortWriter("data").use { writer ->
      inputs["data"]?.use { reader ->
        var index = 0L
        var row = reader.read()

        while (row != null) {
          val valueRaw = row[valueCol]
          val value = (valueRaw as? Number)?.toDouble() ?: 0.0

          // Calculate Z-score for this value
          val zScore = (value - mean) / std

          // Flag as outlier if absolute Z-score exceeds threshold
          val isOutlier = kotlin.math.abs(zScore) > zThreshold

          if (isOutlier) {
            outlierCount++
            LOGGER.debug("Outlier detected: value=$value, z-score=$zScore")
          }

          // Write the original row with the outlier flag added
          writer.write(index, row + mapOf(outputColumnName to isOutlier))
          index++
          row = reader.read()
        }
      }
    }

    LOGGER.info("Detected $outlierCount outliers out of $count rows (threshold: $zThreshold)")
  }
}
