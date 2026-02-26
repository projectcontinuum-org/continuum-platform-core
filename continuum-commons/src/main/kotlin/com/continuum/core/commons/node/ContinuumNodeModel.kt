package com.continuum.core.commons.node

import com.continuum.core.commons.model.ContinuumWorkflowModel

interface ContinuumNodeModel {
  /**
   * Optional markdown documentation describing the node's functionality, inputs, outputs, and examples.
   * Should include usage examples and detailed explanations of behavior.
   */
  val documentationMarkdown: String?
  val categories: List<String>
  val metadata: ContinuumWorkflowModel.NodeData
}