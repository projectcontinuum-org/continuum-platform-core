package com.continuum.core.worker.node

import com.continuum.core.commons.node.TriggerNodeModel
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TimeTriggerNodeModel : TriggerNodeModel() {
    override fun execute(): Map<String, Any?> {
        return mapOf(
            "output-1" to "Hello world at ${Instant.now()}"
        )
    }

}