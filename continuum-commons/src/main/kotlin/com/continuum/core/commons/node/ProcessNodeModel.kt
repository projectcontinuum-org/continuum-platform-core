package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel

abstract class ProcessNodeModel {
    abstract val inputPorts: Map<String, ContinuumWorkflowModel.NodePort>
    abstract val outputPorts: Map<String, ContinuumWorkflowModel.NodePort>

    fun run(
        inputs: Map<String, Any>
    ): Map<String, Any?> {
        return execute(inputs)
    }

    abstract fun execute(inputs: Map<String, Any>): Map<String, Any?>
}