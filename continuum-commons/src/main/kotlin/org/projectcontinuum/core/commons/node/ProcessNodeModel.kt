package org.projectcontinuum.core.commons.node

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.core.commons.utils.ValidationHelper
import jakarta.annotation.PostConstruct
import org.projectcontinuum.core.commons.protocol.progress.NodeProgress

abstract class ProcessNodeModel : ContinuumNodeModel {
  /**
   * Optional markdown documentation describing the node's functionality, inputs, outputs, and examples.
   * Should include usage examples and detailed explanations of behavior.
   * Automatically loaded from resources/com/continuum/base/node/[ClassName].md
   */
  override var documentationMarkdown: String? = null

  @PostConstruct
  fun loadDocumentationFromResources() {
    if (documentationMarkdown == null) {
      val resourcePath = this::class.java.`package`.name.replace(".", "/") + "/${this::class.java.simpleName}.doc.md"
      documentationMarkdown = this::class.java.classLoader
        .getResource(resourcePath)
        ?.readText(Charsets.UTF_8)
        ?: "Documentation not found for ${this::class.java.simpleName}"
    }
  }

  abstract val inputPorts: Map<String, ContinuumWorkflowModel.NodePort>
  abstract val outputPorts: Map<String, ContinuumWorkflowModel.NodePort>

  open fun run(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback
  ) {
    // Validate properties
    ValidationHelper.Companion.validateJsonWithSchema(
      node.data.properties,
      node.data.propertiesSchema
    )

    execute(
      node.data.properties,
      inputs,
      nodeOutputWriter,
      nodeProgressCallback
    )
  }

  abstract fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback = object : NodeProgressCallback {
      override fun report(nodeProgress: NodeProgress) {}
      override fun report(progressPercentage: Int) {}
    }
  )

}