package org.projectcontinuum.core.commons.node

import org.projectcontinuum.core.commons.context.ExecutionContext
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
    run(node, inputs, nodeOutputWriter, nodeProgressCallback, ExecutionContext.EMPTY)
  }

  /**
   * Runs the node with an [ExecutionContext] containing resolved credentials and owner info.
   *
   * Called by the worker framework. Validates properties, then delegates to the
   * [execute] overload that accepts [ExecutionContext].
   */
  open fun run(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback,
    executionContext: ExecutionContext
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
      nodeProgressCallback,
      executionContext
    )
  }

  open fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback = object : NodeProgressCallback {
      override fun report(nodeProgress: NodeProgress) {}
      override fun report(progressPercentage: Int) {}
    }
  ) {
    // Default implementation does nothing. Override this method for nodes that don't need credentials.
  }

  /**
   * Overload of [execute] that provides an [ExecutionContext] with resolved credentials
   * and owner information. Override this method instead of the base [execute] when your
   * node needs access to credentials or owner identity.
   *
   * The default implementation delegates to the base [execute], so existing nodes
   * that don't need credentials continue to work unchanged.
   *
   * ## Example
   * ```kotlin
   * override fun execute(
   *     properties: Map<String, Any>?,
   *     inputs: Map<String, NodeInputReader>,
   *     nodeOutputWriter: NodeOutputWriter,
   *     nodeProgressCallback: NodeProgressCallback,
   *     executionContext: ExecutionContext
   * ) {
   *     val creds = executionContext.getCredential("AWS Credentials")
   *         ?: throw NodeRuntimeException(...)
   *     // use creds...
   * }
   * ```
   */
  open fun execute(
    properties: Map<String, Any>?,
    inputs: Map<String, NodeInputReader>,
    nodeOutputWriter: NodeOutputWriter,
    nodeProgressCallback: NodeProgressCallback= object : NodeProgressCallback {
      override fun report(nodeProgress: NodeProgress) {}
      override fun report(progressPercentage: Int) {}
    },
    executionContext: ExecutionContext
  ) {
    // Default: delegate to the base execute() for backward compatibility
    execute(properties, inputs, nodeOutputWriter, nodeProgressCallback)
  }

}