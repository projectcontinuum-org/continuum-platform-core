package com.continuum.core.worker.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.model.PortDataStatus
import com.continuum.core.commons.node.TriggerNodeModel
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TimeTriggerNodeModel : TriggerNodeModel() {
    override val categories = listOf(
        "Trigger"
    )

    private final val outputPorts = mapOf(
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
    )

    override fun execute(): Map<String, PortData> {
        return mapOf(
            "output-1" to PortData(
                status = PortDataStatus.SUCCESS,
                contentType = TEXT_PLAIN_VALUE,
                data = "Hello world from ${Instant.now()}"
            )
        )
    }
}