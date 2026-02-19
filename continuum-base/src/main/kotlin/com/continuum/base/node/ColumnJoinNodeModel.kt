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
class ColumnJoinNodeModel: ProcessNodeModel() {
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

    private val _documentationMarkdown = ""
    
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
        object: TypeReference<Map<String, Any>>() {}
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
        object: TypeReference<Map<String, Any>>() {}
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

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
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
        
        nodeOutputWriter.createOutputPortWriter("output").use { writer ->
            inputs["left"]?.use { leftReader ->
                inputs["right"]?.use { rightReader ->
                    var leftRow = leftReader.read()
                    var rightRow = rightReader.read()
                    var rowNumber = 0L
                    
                    while (leftRow != null && rightRow != null) {
                        val leftValue = leftRow[columnNameLeft] as? String ?: ""
                        val rightValue = rightRow[columnNameRight] as? String ?: ""
                        val joinedValue = "$leftValue $rightValue".trim()
                        
                        writer.write(rowNumber, mapOf(
                            outputColumnName to joinedValue
                        ))
                        
                        leftRow = leftReader.read()
                        rightRow = rightReader.read()
                        rowNumber++
                    }
                }
            }
        }
    }
}
