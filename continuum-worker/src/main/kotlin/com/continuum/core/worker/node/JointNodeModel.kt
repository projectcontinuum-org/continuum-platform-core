package com.continuum.core.worker.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

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
    )

    override fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        // Wait for random seconds
//        Thread.sleep((1..5).random() * 500L)
        LOGGER.info("Jointing the input: ${objectMapper.writeValueAsString(inputs)}")
        nodeOutputWriter.createOutputPortWriter("output-1").use { writer ->
            inputs["input-1"]?.use { reader1 ->
                inputs["input-2"]?.use { reader2 ->
                    var input1 = reader1.read()
                    var input2 = reader2.read()
                    while (input1 != null && input2 != null) {
                        val rowNumber = input1.rowNumber
                        val dataCells = "${StandardCharsets.UTF_8.decode(input1.cells.first().value)} ${StandardCharsets.UTF_8.decode(input2.cells.first().value)}"
                        writer.write(rowNumber, mapOf(
                            "message" to dataCells
                        ))
                        input1 = reader1.read()
                        input2 = reader2.read()
                    }
                }
            }
        }

    }

}