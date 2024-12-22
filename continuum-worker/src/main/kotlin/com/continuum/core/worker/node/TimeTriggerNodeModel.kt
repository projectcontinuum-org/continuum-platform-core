package com.continuum.core.worker.node

import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.model.PortDataStatus
import com.continuum.core.commons.node.TriggerNodeModel
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TimeTriggerNodeModel : TriggerNodeModel() {
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