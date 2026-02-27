package com.continuum.base.node

import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.prototol.progress.NodeProgressCallback
import com.continuum.core.commons.prototol.progress.NodeProgress
import com.continuum.core.commons.prototol.progress.StageStatus
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
 * **Implementation**:
 * This implementation uses a three-pass streaming algorithm with NodeInputReader.reset() to handle
 * large datasets efficiently without loading all rows into memory:
 * 1. First pass: Calculate mean by summing all values
 * 2. Reset stream and second pass: Calculate standard deviation
 * 3. Reset stream and third pass: Flag outliers based on Z-score threshold
 *
 * The reset() method reopens the Parquet file for each pass, maintaining memory efficiency
 * while allowing multiple iterations over the data.
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

    // Stage names for progress reporting
    private const val STAGE_CALCULATE_MEAN = "Calculate Mean"
    private const val STAGE_CALCULATE_STD = "Calculate Standard Deviation"
    private const val STAGE_FLAG_OUTLIERS = "Flag Outliers"
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
    title = "Anomaly Detector",
    subTitle = "(Z-Score) outlier detection",
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
   * This implementation uses a three-pass streaming algorithm with NodeInputReader.reset() to avoid
   * storing all rows in memory, which is critical for handling large datasets:
   * 1. First pass: Calculate the mean by accumulating sum and count
   * 2. Reset stream and second pass: Calculate standard deviation using the computed mean
   * 3. Reset stream and third pass: Flag outliers and write output rows
   *
   * The reset() method reopens the underlying Parquet file between passes, allowing the data
   * to be re-read from the beginning while maintaining constant memory usage.
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
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback
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

    // Helper function to report stage progress
    fun reportStageProgress(
      progressPercentage: Int,
      message: String,
      calculateMean: StageStatus,
      calculateStd: StageStatus,
      flagOutliers: StageStatus
    ) {
      nodeProgressCallback.report(
        NodeProgress(
          progressPercentage = progressPercentage,
          message = message,
          stageStatus = mapOf(
            STAGE_CALCULATE_MEAN to calculateMean,
            STAGE_CALCULATE_STD to calculateStd,
            STAGE_FLAG_OUTLIERS to flagOutliers
          )
        )
      )
    }

    // Progress reporting interval in milliseconds (report every X seconds)
    val progressReportIntervalMs = 500L
    var lastProgressReportTime = System.currentTimeMillis()

    // Declare variables for all passes outside the .use block
    var sum = 0.0
    var count = 0L
    var nullCount = 0
    var sumSquaredDiff = 0.0
    var mean = 0.0
    var std = 0.0

    // Report initial state - all stages pending
    reportStageProgress(
      progressPercentage = 0,
      message = "Starting anomaly detection",
      calculateMean = StageStatus.PENDING,
      calculateStd = StageStatus.PENDING,
      flagOutliers = StageStatus.PENDING
    )

    // ====================================================================================
    // SINGLE .use {} BLOCK FOR ALL THREE PASSES
    // ====================================================================================
    inputs["data"]?.use { reader ->
      // Get total row count for progress calculation
      val totalRows = reader.getRowCount()

      // ========================================
      // FIRST PASS: Calculate mean (0-33%)
      // ========================================
      reportStageProgress(
        progressPercentage = 0,
        message = "Calculating mean... (0/$totalRows rows)",
        calculateMean = StageStatus.IN_PROGRESS,
        calculateStd = StageStatus.PENDING,
        flagOutliers = StageStatus.PENDING
      )

      var row = reader.read()
      var rowsProcessed = 0L

      while (row != null) {
        // Artificial delay for testing progress reporting
        Thread.sleep((10..100).random().toLong())

        val valueRaw = row[valueCol]

        val value = when {
          valueRaw == null -> {
            nullCount++
            0.0
          }
          valueRaw is Number -> valueRaw.toDouble()
          else -> {
            nullCount++
            0.0
          }
        }

        sum += value
        count++
        rowsProcessed++

        // Report progress periodically during first pass (0-33%)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProgressReportTime >= progressReportIntervalMs && totalRows > 0) {
          val passProgress = (rowsProcessed * 33 / totalRows).toInt().coerceAtMost(32)
          reportStageProgress(
            progressPercentage = passProgress,
            message = "Calculating mean... ($rowsProcessed/$totalRows rows)",
            calculateMean = StageStatus.IN_PROGRESS,
            calculateStd = StageStatus.PENDING,
            flagOutliers = StageStatus.PENDING
          )
          lastProgressReportTime = currentTime
        }

        row = reader.read()
      }

      if (nullCount > 0) {
        LOGGER.warn("Found $nullCount null or non-numeric values in column '$valueCol', defaulted to 0.0")
      }

      if (count == 0L) {
        LOGGER.warn("No data to analyze")
        return@use
      }

      mean = sum / count

      reportStageProgress(
        progressPercentage = 33,
        message = "Mean calculated: $mean ($count rows)",
        calculateMean = StageStatus.COMPLETED,
        calculateStd = StageStatus.PENDING,
        flagOutliers = StageStatus.PENDING
      )

      reader.reset()
      lastProgressReportTime = System.currentTimeMillis()

      // ========================================
      // SECOND PASS: Calculate standard deviation (33-66%)
      // ========================================
      reportStageProgress(
        progressPercentage = 33,
        message = "Calculating standard deviation... (0/$totalRows rows)",
        calculateMean = StageStatus.COMPLETED,
        calculateStd = StageStatus.IN_PROGRESS,
        flagOutliers = StageStatus.PENDING
      )

      row = reader.read()
      rowsProcessed = 0L

      while (row != null) {
        // Artificial delay for testing progress reporting
        Thread.sleep((10..100).random().toLong())

        val valueRaw = row[valueCol]
        val value = (valueRaw as? Number)?.toDouble() ?: 0.0

        sumSquaredDiff += (value - mean).pow(2)
        rowsProcessed++

        // Report progress periodically during second pass (33-66%)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProgressReportTime >= progressReportIntervalMs && totalRows > 0) {
          val passProgress = 33 + (rowsProcessed * 33 / totalRows).toInt().coerceAtMost(32)
          reportStageProgress(
            progressPercentage = passProgress,
            message = "Calculating standard deviation... ($rowsProcessed/$totalRows rows)",
            calculateMean = StageStatus.COMPLETED,
            calculateStd = StageStatus.IN_PROGRESS,
            flagOutliers = StageStatus.PENDING
          )
          lastProgressReportTime = currentTime
        }

        row = reader.read()
      }

      std = sqrt(sumSquaredDiff / count)

      LOGGER.info("Statistics: mean=$mean, std=$std, count=$count")

      reportStageProgress(
        progressPercentage = 66,
        message = "Standard deviation calculated: $std",
        calculateMean = StageStatus.COMPLETED,
        calculateStd = StageStatus.COMPLETED,
        flagOutliers = StageStatus.PENDING
      )

      reader.reset()
      lastProgressReportTime = System.currentTimeMillis()

      // ========================================
      // THIRD PASS: Flag outliers and write output (66-100%)
      // ========================================
      reportStageProgress(
        progressPercentage = 66,
        message = "Flagging outliers... (0/$totalRows rows)",
        calculateMean = StageStatus.COMPLETED,
        calculateStd = StageStatus.COMPLETED,
        flagOutliers = StageStatus.IN_PROGRESS
      )

      if (std == 0.0) {
        LOGGER.warn("Standard deviation is 0, all values are the same - no outliers detected")

        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
          var index = 0L
          row = reader.read()

          while (row != null) {
            // Artificial delay for testing progress reporting
            Thread.sleep((10..100).random().toLong())

            val currentRow = row!!
            writer.write(index, currentRow + mapOf(outputColumnName to false))
            index++

            // Report progress periodically during third pass (66-100%)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressReportTime >= progressReportIntervalMs && totalRows > 0) {
              val passProgress = 66 + (index * 34 / totalRows).toInt().coerceAtMost(33)
              reportStageProgress(
                progressPercentage = passProgress,
                message = "Flagging outliers... ($index/$totalRows rows)",
                calculateMean = StageStatus.COMPLETED,
                calculateStd = StageStatus.COMPLETED,
                flagOutliers = StageStatus.IN_PROGRESS
              )
              lastProgressReportTime = currentTime
            }

            row = reader.read()
          }
        }

        reportStageProgress(
          progressPercentage = 100,
          message = "Completed: No outliers (all values identical)",
          calculateMean = StageStatus.COMPLETED,
          calculateStd = StageStatus.COMPLETED,
          flagOutliers = StageStatus.COMPLETED
        )
      } else {
        var outlierCount = 0

        nodeOutputWriter.createOutputPortWriter("data").use { writer ->
          var index = 0L
          row = reader.read()

          while (row != null) {
            // Artificial delay for testing progress reporting (500-1000ms)
            Thread.sleep((10..100).random().toLong())

            val currentRow = row!!
            val valueRaw = currentRow[valueCol]
            val value = (valueRaw as? Number)?.toDouble() ?: 0.0

            val zScore = (value - mean) / std
            val isOutlier = kotlin.math.abs(zScore) > zThreshold

            if (isOutlier) {
              outlierCount++
            }

            writer.write(index, currentRow + mapOf(outputColumnName to isOutlier))
            index++

            // Report progress periodically during third pass (66-100%)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProgressReportTime >= progressReportIntervalMs && totalRows > 0) {
              val passProgress = 66 + (index * 34 / totalRows).toInt().coerceAtMost(33)
              reportStageProgress(
                progressPercentage = passProgress,
                message = "Flagging outliers... ($index/$totalRows rows, $outlierCount outliers found)",
                calculateMean = StageStatus.COMPLETED,
                calculateStd = StageStatus.COMPLETED,
                flagOutliers = StageStatus.IN_PROGRESS
              )
              lastProgressReportTime = currentTime
            }

            row = reader.read()
          }
        }

        LOGGER.info("Detected $outlierCount outliers out of $count rows (threshold: $zThreshold)")

        reportStageProgress(
          progressPercentage = 100,
          message = "Completed: Detected $outlierCount outliers out of $count rows",
          calculateMean = StageStatus.COMPLETED,
          calculateStd = StageStatus.COMPLETED,
          flagOutliers = StageStatus.COMPLETED
        )
      }
    }
  }
}
