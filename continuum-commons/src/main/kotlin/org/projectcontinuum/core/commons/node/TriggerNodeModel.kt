package org.projectcontinuum.core.commons.node

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import jakarta.annotation.PostConstruct

abstract class TriggerNodeModel : ContinuumNodeModel {
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

  abstract val outputPorts: Map<String, ContinuumWorkflowModel.NodePort>

  /**
   * Called by the worker framework. Validates properties, then calls [execute].
   *
   * @throws org.projectcontinuum.core.commons.exception.NodeRuntimeException non-retriable, when the properties
   *   do not match `propertiesSchema`; [execute] is not called.
   */
  fun run(
    node: ContinuumWorkflowModel.Node,
    nodeOutputWriter: NodeOutputWriter
  ) {
    node.validatePropertiesOrFail()
    return execute(
      node.data.properties,
      nodeOutputWriter
    )
  }

  abstract fun execute(properties: Map<String, Any>?, nodeOutputWriter: NodeOutputWriter)
}