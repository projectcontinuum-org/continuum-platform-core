package com.continuum.core.worker.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.model.PortDataStatus
import com.continuum.core.commons.node.ProcessNodeModel
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component
import org.springframework.util.MimeType

@Component
class SplitNodeModel : ProcessNodeModel() {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(SplitNodeModel::class.java)
        private val objectMapper = ObjectMapper()
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

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Split the input string into two parts",
        title = "Split Node",
        subTitle = "Split the input string into two parts",
        nodeModel = this.javaClass.name,
        icon = """
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" stroke="currentColor" strokeWidth={1.5} viewBox="0 0 24 24">
                <path d="M7 7V1.414a1 1 0 0 1 2 0V2h5a1 1 0 0 1 .8.4l.975 1.3a.5.5 0 0 1 0 .6L14.8 5.6a1 1 0 0 1-.8.4H9v10H7v-5H2a1 1 0 0 1-.8-.4L.225 9.3a.5.5 0 0 1 0-.6L1.2 7.4A1 1 0 0 1 2 7zm1 3V8H2l-.75 1L2 10zm0-5h6l.75-1L14 3H8z"/>
            </svg>
        """.trimIndent(),
        inputs = inputPorts,
        outputs = outputPorts,
    )

    override fun execute(inputs: Map<String, PortData>): Map<String, PortData> {
        LOGGER.info("Splitting the input: ${objectMapper.writeValueAsString(inputs)}")
        // Wait for random seconds
        Thread.sleep((1..5).random() * 1000L)
        inputs["input-1"]?.let { portData ->
            if(portData.status == PortDataStatus.SUCCESS && portData.contentType == TEXT_PLAIN_VALUE) {
                LOGGER.info("Input data: '${portData.data}'")
                return splitString(portData.data.toString())
            } else {
                throw IllegalArgumentException("Invalid input data")
            }
        }
        throw IllegalArgumentException("Invalid input data")
    }

    fun splitString(
        stringToSplit: String
    ): Map<String, PortData> {
        val parts = stringToSplit.split(" ", limit = 2)
        return if (parts.size > 1) {
            mapOf(
                "output-1" to PortData(
                    status = PortDataStatus.SUCCESS,
                    contentType = TEXT_PLAIN_VALUE,
                    data = parts[1]
                ),
                "output-2" to PortData(
                    status = PortDataStatus.SUCCESS,
                    contentType = TEXT_PLAIN_VALUE,
                    data = parts[0]
                )
            )
        } else {
            mapOf(
                "output-1" to PortData(
                    status = PortDataStatus.SUCCESS,
                    contentType = TEXT_PLAIN_VALUE,
                    data = parts[0]
                )
            )
        }
    }
}