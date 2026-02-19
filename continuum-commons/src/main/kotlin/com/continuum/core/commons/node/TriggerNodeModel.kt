package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.utils.NodeOutputWriter
import jakarta.annotation.PostConstruct

abstract class TriggerNodeModel: ContinuumNodeModel {
    /**
     * Optional markdown documentation describing the node's functionality, inputs, outputs, and examples.
     * Should include usage examples and detailed explanations of behavior.
     * Automatically loaded from resources/com/continuum/base/node/[ClassName].md
     */
    override var documentationMarkdown: String? = null
    
    @PostConstruct
    fun loadDocumentationFromResources() {
        if (documentationMarkdown == null) {
            documentationMarkdown = this::class.java.classLoader
                .getResource("com/continuum/base/node/${this::class.java.simpleName}.md")
                ?.readText(Charsets.UTF_8)
                ?: "Documentation not found for ${this::class.java.simpleName}"
        }
    }

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