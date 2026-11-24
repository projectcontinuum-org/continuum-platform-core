package org.projectcontinuum.core.commons.node

import org.projectcontinuum.core.commons.context.ExecutionContext
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
   * Validates properties, then calls [execute] with an empty [ExecutionContext].
   *
   * @throws org.projectcontinuum.core.commons.exception.NodeRuntimeException non-retriable, when the properties
   *   do not match `propertiesSchema`; [execute] is not called.
   */
  fun run(
    node: ContinuumWorkflowModel.Node,
    nodeOutputWriter: NodeOutputWriter
  ) {
    run(node = node, nodeOutputWriter = nodeOutputWriter, executionContext = ExecutionContext.EMPTY)
  }

  /**
   * Called by the worker framework. Validates properties, then calls the [execute] overload that accepts an
   * [ExecutionContext], so the node can read credentials and set initial flow variables.
   *
   * @throws org.projectcontinuum.core.commons.exception.NodeRuntimeException non-retriable, when the properties
   *   do not match `propertiesSchema`; [execute] is not called.
   */
  fun run(
    node: ContinuumWorkflowModel.Node,
    nodeOutputWriter: NodeOutputWriter,
    executionContext: ExecutionContext
  ) {
    validatePropertiesOrFail(node)
    execute(
      properties = node.data.properties,
      nodeOutputWriter = nodeOutputWriter,
      executionContext = executionContext
    )
  }

  /** Override this for nodes that need neither credentials nor flow variables. Default does nothing. */
  open fun execute(properties: Map<String, Any>?, nodeOutputWriter: NodeOutputWriter) {
    // Default implementation does nothing. Override this or the ExecutionContext overload.
  }

  /**
   * Overload of [execute] with an [ExecutionContext]. Override it instead of the base [execute] when the node needs
   * credentials or sets flow variables (`executionContext.setFlowVariable(...)`).
   *
   * The default implementation delegates to the base [execute], so existing nodes continue to work unchanged.
   */
  open fun execute(
    properties: Map<String, Any>?,
    nodeOutputWriter: NodeOutputWriter,
    executionContext: ExecutionContext
  ) {
    execute(properties = properties, nodeOutputWriter = nodeOutputWriter)
  }
}