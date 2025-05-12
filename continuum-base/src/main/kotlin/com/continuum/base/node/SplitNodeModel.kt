package com.continuum.base.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.continuum.knime.base.node.org.knime.base.node.preproc.filter.row.RowFilterNodeModel
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

@Component
class SplitNodeModel : ProcessNodeModel() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(SplitNodeModel::class.java)
        private val objectMapper = jacksonObjectMapper()
        private val resourceRootPath = RowFilterNodeModel::class.java.name.split(".").joinToString(File.separator)
    }

    final override val inputPorts = mapOf(
        "input-1" to ContinuumWorkflowModel.NodePort(
            name = "input string",
            contentType = "string"
        )
    )

    final override val outputPorts = mapOf(
        "output-1" to ContinuumWorkflowModel.NodePort(
            name = "part 1",
            contentType = "string"
        ),
        "output-2" to ContinuumWorkflowModel.NodePort(
            name = "part 2",
            contentType = "string"
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
            "columnName": {
              "type": "string",
              "title": "Column Name",
              "description": "The name of the column to split"
            }
          },
          "required": ["columnName"]
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
              "scope": "#/properties/columnName"
            }
          ]
        }
        """.trimIndent(),
        object: TypeReference<Map<String, Any>>() {}
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Split a column into two parts",
        title = "Column Splitter",
        subTitle = "Split a column",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path d="M7 7V1.414a1 1 0 0 1 2 0V2h5a1 1 0 0 1 .8.4l.975 1.3a.5.5 0 0 1 0 .6L14.8 5.6a1 1 0 0 1-.8.4H9v10H7v-5H2a1 1 0 0 1-.8-.4L.225 9.3a.5.5 0 0 1 0-.6L1.2 7.4A1 1 0 0 1 2 7zm1 3V8H2l-.75 1L2 10zm0-5h6l.75-1L14 3H8z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
        properties = mapOf(
            "columnName" to "message"
        ),
        propertiesSchema = propertiesSchema,
        propertiesUISchema = propertiesUiSchema
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        LOGGER.info("Splitting the input: ${objectMapper.writeValueAsString(inputs)}")
        // Wait for random seconds
//        Thread.sleep((1..5).random() * 500L)
        splitString(inputs["input-1"]!!, nodeOutputWriter)
    }

    fun splitString(
        nodeInputReader: NodeInputReader,
        nodeOutputWriter: NodeOutputWriter
    ) {
        nodeInputReader.use { reader ->
            var input = reader.read()
            val outputWriters = mutableListOf(
                nodeOutputWriter.createOutputPortWriter("output-1")
            )
            var rowNumber = 0L
            while (input != null) {
                val stringToSplit = input.entries.first().value as String
                val parts = stringToSplit.split(" ", limit = 2)
                if(parts.size > outputWriters.size) {
                    outputWriters.add(nodeOutputWriter.createOutputPortWriter("output-${outputWriters.size + 1}"))
                }
                parts.forEachIndexed { index, part ->
                    outputWriters[index].write(rowNumber, mapOf(
                        "part-${index + 1}" to part
                    ))
                }
                input = reader.read()
                rowNumber++
            }
            outputWriters.forEach { it.close() }
        }
    }
}