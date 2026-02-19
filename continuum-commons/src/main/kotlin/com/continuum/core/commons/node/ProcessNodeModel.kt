package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.continuum.core.commons.utils.ValidationHelper

abstract class ProcessNodeModel: ContinuumNodeModel {
    abstract val inputPorts: Map<String, ContinuumWorkflowModel.NodePort>
    abstract val outputPorts: Map<String, ContinuumWorkflowModel.NodePort>
    
    /**
     * Optional markdown documentation describing the node's functionality, inputs, outputs, and examples.
     * Should include usage examples and detailed explanations of behavior.
     */
    open val documentationMarkdown: String? = null

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

    abstract fun execute(
        properties: Map<String, Any>?,
        inputs: Map<String, NodeInputReader>,
        nodeOutputWriter: NodeOutputWriter
    )
}