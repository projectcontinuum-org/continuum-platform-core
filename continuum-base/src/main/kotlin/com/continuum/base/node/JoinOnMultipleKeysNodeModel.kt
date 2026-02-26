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
class JoinOnMultipleKeysNodeModel : ProcessNodeModel() {
  companion object {
    private val LOGGER = LoggerFactory.getLogger(JoinOnMultipleKeysNodeModel::class.java)
    private val objectMapper = ObjectMapper()
  }

  final override val inputPorts = mapOf(
    "left" to ContinuumWorkflowModel.NodePort(
      name = "left table",
      contentType = TEXT_PLAIN_VALUE
    ),
    "right" to ContinuumWorkflowModel.NodePort(
      name = "right table",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  final override val outputPorts = mapOf(
    "data" to ContinuumWorkflowModel.NodePort(
      name = "joined table",
      contentType = TEXT_PLAIN_VALUE
    )
  )

  override val categories = listOf(
    "Join & Merge"
  )

  val propertiesSchema: Map<String, Any> = objectMapper.readValue(
    """
        {
          "type": "object",
          "properties": {
            "leftKey1": {
              "type": "string",
              "title": "Left Key 1",
              "description": "First key column from left table"
            },
            "leftKey2": {
              "type": "string",
              "title": "Left Key 2",
              "description": "Second key column from left table"
            },
            "rightKey1": {
              "type": "string",
              "title": "Right Key 1",
              "description": "First key column from right table"
            },
            "rightKey2": {
              "type": "string",
              "title": "Right Key 2",
              "description": "Second key column from right table"
            }
          },
          "required": ["leftKey1", "leftKey2", "rightKey1", "rightKey2"]
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
              "type": "Group",
              "label": "Left Table Keys",
              "elements": [
                {
                  "type": "Control",
                  "scope": "#/properties/leftKey1"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/leftKey2"
                }
              ]
            },
            {
              "type": "Group",
              "label": "Right Table Keys",
              "elements": [
                {
                  "type": "Control",
                  "scope": "#/properties/rightKey1"
                },
                {
                  "type": "Control",
                  "scope": "#/properties/rightKey2"
                }
              ]
            }
          ]
        }
        """.trimIndent(),
    object : TypeReference<Map<String, Any>>() {}
  )

  override val metadata = ContinuumWorkflowModel.NodeData(
    id = this.javaClass.name,
    description = "Performs inner join on two tables using two key columns from each table",
    title = "Join on Multiple Keys",
    subTitle = "Inner join with composite keys",
    nodeModel = this.javaClass.name,
    icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 16.875h3.375m0 0h3.375m-3.375 0V13.5m0 3.375v3.375M6 10.5h2.25a2.25 2.25 0 002.25-2.25V6a2.25 2.25 0 00-2.25-2.25H6A2.25 2.25 0 003.75 6v2.25A2.25 2.25 0 006 10.5zm0 9.75h2.25A2.25 2.25 0 0010.5 18v-2.25a2.25 2.25 0 00-2.25-2.25H6a2.25 2.25 0 00-2.25 2.25V18A2.25 2.25 0 006 20.25zm9.75-9.75H18a2.25 2.25 0 002.25-2.25V6A2.25 2.25 0 0018 3.75h-2.25A2.25 2.25 0 0013.5 6v2.25a2.25 2.25 0 002.25 2.25z" />
            </svg>
        """.trimIndent(),
    inputs = inputPorts,
    outputs = outputPorts,
    properties = mapOf(
      "leftKey1" to "id",
      "leftKey2" to "date",
      "rightKey1" to "id",
      "rightKey2" to "date"
    ),
    propertiesSchema = propertiesSchema,
    propertiesUISchema = propertiesUiSchema
  )

  override fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter
  ) {
    val leftKey1 = properties?.get("leftKey1") as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "leftKey1 is not provided"
    )
    val leftKey2 = properties["leftKey2"] as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "leftKey2 is not provided"
    )
    val rightKey1 = properties["rightKey1"] as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "rightKey1 is not provided"
    )
    val rightKey2 = properties["rightKey2"] as String? ?: throw NodeRuntimeException(
      workflowId = "",
      nodeId = "",
      message = "rightKey2 is not provided"
    )

    LOGGER.info("Joining on multiple keys: left[$leftKey1,$leftKey2] = right[$rightKey1,$rightKey2]")

    // Read all left rows
    val leftRows = mutableListOf<Map<String, Any>>()
    inputs["left"]?.use { reader ->
      var row = reader.read()
      while (row != null) {
        leftRows.add(row)
        row = reader.read()
      }
    }

    // Read all right rows
    val rightRows = mutableListOf<Map<String, Any>>()
    inputs["right"]?.use { reader ->
      var row = reader.read()
      while (row != null) {
        rightRows.add(row)
        row = reader.read()
      }
    }

    LOGGER.info("Read ${leftRows.size} left rows and ${rightRows.size} right rows")

    // Build hash map for efficient lookup
    val rightIndex = mutableMapOf<Pair<Any?, Any?>, MutableList<Map<String, Any>>>()
    rightRows.forEach { rightRow ->
      val key = Pair(rightRow[rightKey1], rightRow[rightKey2])
      rightIndex.getOrPut(key) { mutableListOf() }.add(rightRow)
    }

    // Perform join
    nodeOutputWriter.createOutputPortWriter("data").use { writer ->
      var rowNumber = 0L

      leftRows.forEach { leftRow ->
        val key = Pair(leftRow[leftKey1], leftRow[leftKey2])
        val matchingRightRows = rightIndex[key]

        matchingRightRows?.forEach { rightRow ->
          val joinedRow = leftRow + rightRow
          writer.write(rowNumber, joinedRow)
          rowNumber++
        }
      }

      LOGGER.info("Joined to $rowNumber rows")
    }
  }
}
