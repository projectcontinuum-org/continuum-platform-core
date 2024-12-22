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

    override val inputPorts = mapOf(
        "input-1" to ContinuumWorkflowModel.NodePort(
            name = "input string",
            contentType = "string"
        )
    )

    override val outputPorts = mapOf(
        "output-1" to ContinuumWorkflowModel.NodePort(
            name = "part 1",
            contentType = "string"
        ),
        "output-2" to ContinuumWorkflowModel.NodePort(
            name = "part 2",
            contentType = "string"
        )
    )

    override fun execute(inputs: Map<String, PortData>): Map<String, PortData> {
        LOGGER.info("Splitting the input: ${objectMapper.writeValueAsString(inputs)}")
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