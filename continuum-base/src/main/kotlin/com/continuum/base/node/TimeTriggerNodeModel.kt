package com.continuum.base.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.node.TriggerNodeModel
import com.continuum.core.commons.utils.NodeOutputWriter
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TimeTriggerNodeModel : TriggerNodeModel() {
    override val categories = listOf(
        "Trigger"
    )

    final override val outputPorts = mapOf(
        "output-1" to ContinuumWorkflowModel.NodePort(
            name = "output-1",
            contentType = TEXT_PLAIN_VALUE
        )
    )

    override val metadata = ContinuumWorkflowModel.NodeData(
        id = this.javaClass.name,
        description = "Starts the workflow execution with the current time as the output",
        title = "Start Node",
        subTitle = "Starts the workflow execution",
        nodeModel = this.javaClass.name,
        icon = "mui/Bolt",
        outputs = outputPorts,
        properties = mapOf(
            "message" to "Logging at",
            "rowCount" to 10
        ),
        propertiesSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "message" to mapOf(
                    "type" to "string"
                ),
                "rowCount" to mapOf(
                    "type" to "number",
                    "minimum" to 1,
                )
            ),
            "required" to listOf(
                "message",
                "rowCount"
            )
        ),
        propertiesUISchema = mapOf(
            "type" to "VerticalLayout",
            "elements" to listOf(
                mapOf(
                    "type" to "Control",
                    "scope" to "#/properties/message"
                ),
                mapOf(
                    "type" to "Control",
                    "scope" to "#/properties/rowCount"
                )
            )
        )
    )

    override fun execute(
        properties: Map<String, Any>?,
        nodeOutputWriter: NodeOutputWriter
    ) {
        val rowCount = properties?.get("rowCount").toString().toLongOrNull() ?: 10L
        val message = properties?.get("message") as? String ?: "Logging at"
        nodeOutputWriter.createOutputPortWriter("output-1").use {
            for (i in 0 until rowCount) {
                it.write(i, mapOf(
                    "message" to "$message ${Instant.now()}"
                ))
            }
        }
    }
}