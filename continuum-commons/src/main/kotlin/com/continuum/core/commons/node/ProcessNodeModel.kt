package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.continuum.core.commons.utils.ValidationHelper

abstract class ProcessNodeModel: ContinuumNodeModel {
    abstract val inputPorts: Map<String, ContinuumWorkflowModel.NodePort>
    abstract val outputPorts: Map<String, ContinuumWorkflowModel.NodePort>

    open fun run(
        node: ContinuumWorkflowModel.Node,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    ) {
        // Validate properties
        ValidationHelper.validateJsonWithSchema(
            node.data.properties,
            node.data.propertiesSchema
        )

        execute(
            node.data.properties,
            inputs,
            nodeOutputWriter
        )
    }

    abstract fun execute(properties: Map<String, Any>?, inputs: Map<String, NodeInputReader>, nodeOutputWriter: NodeOutputWriter)
}