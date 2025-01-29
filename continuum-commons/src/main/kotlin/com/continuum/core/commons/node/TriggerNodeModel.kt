package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.utils.NodeOutputWriter

abstract class TriggerNodeModel: ContinuumNodeModel {
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