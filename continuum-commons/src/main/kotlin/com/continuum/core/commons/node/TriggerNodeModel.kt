package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.utils.NodeOutputWriter

abstract class TriggerNodeModel: ContinuumNodeModel {
    /**
     * Optional markdown documentation describing the node's functionality, inputs, outputs, and examples.
     * Should include usage examples and detailed explanations of behavior.
     */
    override val documentationMarkdown: String? = """
      ## ToDo: Add documentation for this node.
    """.trimIndent()

    abstract val outputPorts: Map<String, ContinuumWorkflowModel.NodePort>

    fun run(
        node: ContinuumWorkflowModel.Node,
        nodeOutputWriter: NodeOutputWriter
    ) {
        return execute(
            node.data.properties,
            nodeOutputWriter
        )
    }

    abstract fun execute(properties: Map<String, Any>?, nodeOutputWriter: NodeOutputWriter)
}