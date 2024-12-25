package com.continuum.core.commons.node

import com.continuum.core.commons.model.PortData

abstract class TriggerNodeModel: ContinuumNodeModel {
    fun run(): Map<String, PortData> {
        return execute()
    }

    abstract fun execute(): Map<String, PortData>
}