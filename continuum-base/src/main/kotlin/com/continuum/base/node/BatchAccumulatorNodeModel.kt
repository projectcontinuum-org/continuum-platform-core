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
 * Node model for grouping rows into batches and enriching them with batch metadata.
 *
 * This node processes input rows and assigns them to batches of a specified size.
 * Each row is enriched with two additional columns:
 * - batch_id: The batch number (1-indexed)
 * - row_count: The total number of rows in that batch
 *
 * **Batching Logic**:
 * - Rows are grouped sequentially into batches of the specified size
 * - The last batch may contain fewer rows if total rows is not evenly divisible by batch size
 * - Batch IDs start at 1 (not 0)
 *
 * **Performance Optimization**:
 * This implementation uses Parquet metadata reading for optimal performance:
 * 1. Read total row count from Parquet file metadata (no data streaming required)
 * 2. Calculate batch sizes based on row count
 * 3. Stream through rows once to enrich them with batch metadata
 *
 * This approach avoids loading all rows into memory and minimizes I/O by reading the data
 * only once, making it suitable for very large datasets.
 *
 * **Input**:
 * - Port "data": Table to be batched
 *
 * **Output**:
 * - Port "data": Original table with added batch_id and row_count columns
 *
 * **Configuration Properties**:
 * - batchSize (required): Number of rows per batch (minimum: 1)
 *
 * **Use Cases**:
 * - Processing large datasets in smaller chunks for API calls
 * - Parallel processing by distributing batches to workers
 * - Rate limiting by processing one batch at a time
 * - Memory management when downstream operations are memory-intensive
 *
 * @see ProcessNodeModel
 * @author Continuum Workflow
 */
@Component
class BatchAccumulatorNodeModel : ProcessNodeModel() {
  companion object {
    private val LOGGER = LoggerFactory.getLogger(BatchAccumulatorNodeModel::class.java)
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
      name = "batched table",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  override val categories = listOf(
    "Aggregation & Grouping"
  )

  val propertiesSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "object",
          "properties": {
            "batchSize": {
              "type": "number",
              "title": "Batch Size",
              "description": "Number of rows per batch",
              "minimum": 1
            }
          },
          "required": ["batchSize"]
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
              "scope": "#/properties/batchSize"
            }
          ]
        }
        """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  override val metadata = ContinuumWorkflowModel.NodeData(
    id = this.javaClass.name,
    description = "Groups rows into batches and adds batch_id and row_count columns",
    title = "Batch Accumulator",
    subTitle = "Batch and label rows",
    nodeModel = this.javaClass.name,
    icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 6.878V6a2.25 2.25 0 012.25-2.25h7.5A2.25 2.25 0 0118 6v.878m-12 0c.235-.083.487-.128.75-.128h10.5c.263 0 .515.045.75.128m-12 0A2.25 2.25 0 004.5 9v.878m13.5-3A2.25 2.25 0 0119.5 9v.878m0 0a2.246 2.246 0 00-.75-.128H5.25c-.263 0-.515.045-.75.128m15 0A2.25 2.25 0 0121 12v6a2.25 2.25 0 01-2.25 2.25H5.25A2.25 2.25 0 013 18v-6c0-.98.626-1.813 1.5-2.122" />
            </svg>
        """.trimIndent(),
    inputs = inputPorts,
    outputs = outputPorts,
    properties = mapOf(
      "batchSize" to 2
    ),
    propertiesSchema = propertiesSchema,
    propertiesUISchema = propertiesUiSchema
  )

  /**
   * Executes the batch accumulation algorithm using an optimized streaming approach.
   *
   * This implementation uses metadata reading for efficiency:
   * 1. Read Parquet metadata to get total row count (no data streaming)
   * 2. Calculate batch sizes based on row count
   * 3. Stream through rows once to enrich them with batch_id and row_count
   *
   * This is more efficient than the two-pass approach as it avoids the first counting pass.
   *
   * @param properties Configuration containing batchSize
   * @param inputs Input data stream containing the table to batch
   * @param nodeOutputWriter Writer for output data with batch metadata
   */
  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter
  ) {
    // Extract and validate configuration properties
    val batchSize = (properties?.get("batchSize") as? Number)?.toInt() ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "batchSize is not provided"
    )

    if (batchSize < 1) {
      throw NodeRuntimeException(
        workflowId = "",
        nodeId = "",
        message = "batchSize must be at least 1"
      )
    }

    LOGGER.info("Batching rows with batchSize=$batchSize")

    // Get the input reader
    val inputReader = inputs["data"] ?: run {
      LOGGER.info("No input data provided")
      return
    }

    // ========================================
    // METADATA READ: Get row count from Parquet metadata
    // ========================================
    // Read the Parquet file metadata to get the total row count
    // This is much faster than streaming through all rows
    val totalRows = inputReader.getRowCount()

    // Handle empty input
    if (totalRows == 0L) {
      LOGGER.info("No rows to batch")
      return
    }

    // Calculate total number of batches (ceiling division)
    val totalBatches = ((totalRows + batchSize - 1) / batchSize).toInt()

    // Pre-calculate the row count for each batch
    // Most batches will have batchSize rows, but the last batch may have fewer
    val batchSizes = mutableMapOf<Int, Int>()

    for (batchId in 1..totalBatches) {
      val startIdx = (batchId - 1) * batchSize
      val endIdx = minOf(startIdx + batchSize, totalRows.toInt())
      batchSizes[batchId] = endIdx - startIdx
    }

    LOGGER.info("Created $totalBatches batches from $totalRows rows")

    // ========================================
    // SINGLE PASS: Enrich rows with batch metadata
    // ========================================
    // Stream through all rows once and add batch_id and row_count columns.
    // We use the pre-calculated batch sizes to enrich each row.
    nodeOutputWriter.createOutputPortWriter("data").use { writer ->
      inputReader.use { reader ->
        var index = 0L
        var row = reader.read()

        while (row != null) {
          // Calculate which batch this row belongs to (1-indexed)
          val batchId = (index / batchSize).toInt() + 1

          // Look up the row count for this batch from our pre-calculated map
          val rowCount = batchSizes[batchId] ?: batchSize

          // Create enriched row with batch metadata
          val enrichedRow = row.toMutableMap().apply {
            this["batch_id"] = batchId
            this["row_count"] = rowCount
          }

          // Write the enriched row to output
          writer.write(index, enrichedRow)
          index++
          row = reader.read()
        }
      }
    }

    LOGGER.info("Enriched all rows with batch information")
  }
}
