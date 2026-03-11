package org.projectcontinuum.core.commons.node

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

interface ContinuumNodeModel {
  /**
   * Optional markdown documentation describing the node's functionality, inputs, outputs, and examples.
   * Should include usage examples and detailed explanations of behavior.
   */
  val documentationMarkdown: String?
  val categories: List<String>
  val metadata: ContinuumWorkflowModel.NodeData
}