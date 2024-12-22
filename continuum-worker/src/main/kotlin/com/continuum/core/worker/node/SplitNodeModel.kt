package com.continuum.core.worker.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.ProcessNodeModel
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

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

    override fun execute(inputs: Map<String, Any>): Map<String, Any?> {
        LOGGER.info("Splitting the input: $${objectMapper.writeValueAsString(inputs)}")
        val parts = inputs["input-1"]?.toString()?.split(
            " ",
            limit = 2
        )!!
        LOGGER.info("Split parts: ${objectMapper.writeValueAsString(parts)}")

        return if (parts.size > 1) {
            mapOf(
                "output-1" to parts[1],
                "output-2" to parts[0]
            )
        } else {
            mapOf(
                "output-1" to parts[0]
            )
        }
    }
}