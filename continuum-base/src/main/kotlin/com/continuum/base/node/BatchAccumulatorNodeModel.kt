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

  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter
  ) {
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

    // First pass: collect all rows
    val allRows = mutableListOf<Map<String, Any>>()
    inputs["data"]?.use { reader ->
      var row = reader.read()
      while (row != null) {
        allRows.add(row)
        row = reader.read()
      }
    }

    if (allRows.isEmpty()) {
      LOGGER.info("No rows to batch")
      return
    }

    // Calculate batch sizes
    val totalRows = allRows.size
    val totalBatches = (totalRows + batchSize - 1) / batchSize // Ceiling division
    val batchSizes = mutableMapOf<Int, Int>()

    for (batchId in 1..totalBatches) {
      val startIdx = (batchId - 1) * batchSize
      val endIdx = minOf(startIdx + batchSize, totalRows)
      batchSizes[batchId] = endIdx - startIdx
    }

    LOGGER.info("Created $totalBatches batches from $totalRows rows")

    // Second pass: enrich rows with batch_id and row_count
    nodeOutputWriter.createOutputPortWriter("data").use { writer ->
      allRows.forEachIndexed { idx, row ->
        val batchId = (idx / batchSize) + 1
        val rowCount = batchSizes[batchId] ?: batchSize

        val enrichedRow = row.toMutableMap().apply {
          this["batch_id"] = batchId
          this["row_count"] = rowCount
        }

        writer.write(idx.toLong(), enrichedRow)
      }
    }

    LOGGER.info("Enriched all rows with batch information")
  }
}
