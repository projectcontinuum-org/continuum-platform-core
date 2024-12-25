package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData

abstract class ProcessNodeModel: ContinuumNodeModel {
    abstract val inputPorts: Map<String, ContinuumWorkflowModel.NodePort>
    abstract val outputPorts: Map<String, ContinuumWorkflowModel.NodePort>

    fun run(
        inputs: Map<String, PortData>
    ): Map<String, PortData> {
        return execute(inputs)
    }

    abstract fun execute(inputs: Map<String, PortData>): Map<String, PortData>
}