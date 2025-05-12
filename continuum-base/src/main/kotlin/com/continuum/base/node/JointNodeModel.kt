package com.continuum.base.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.temporal.failure.ApplicationFailure
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component

@Component
class JointNodeModel: ProcessNodeModel() {

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
        object: TypeReference<Map<String, Any>>() {}
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
        object: TypeReference<Map<String, Any>>() {}
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

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val inputColumnNames = (properties?.get("inputs") as List<Map<String, String>>)
        val inputColumnName1 = inputColumnNames[0]["columnName"] ?: throw ApplicationFailure.newNonRetryableFailure("Input column name is not provided", "NodeConfigException")
        val inputColumnName2 = inputColumnNames[1]["columnName"] ?: throw ApplicationFailure.newNonRetryableFailure("Input column name is not provided", "NodeConfigException")
        val outputColumnName = properties["outputsColumnName"] as String? ?: "message"
        // Wait for random seconds
//        Thread.sleep((1..5).random() * 500L)
        LOGGER.info("Jointing the input: ${objectMapper.writeValueAsString(inputs)}")
        nodeOutputWriter.createOutputPortWriter("output-1").use { writer ->
            inputs["input-1"]?.use { reader1 ->
                inputs["input-2"]?.use { reader2 ->
                    var input1 = reader1.read()
                    var input2 = reader2.read()
                    var rowNumber = 0L
                    while (input1 != null && input2 != null) {
                        val dataCells = "${input1[inputColumnName1] as String} ${input2[inputColumnName2] as String}"
                        writer.write(rowNumber, mapOf(
                            outputColumnName to dataCells
                        ))
                        input1 = reader1.read()
                        input2 = reader2.read()
                        rowNumber++
                    }
                }
            }
        }

    }

}