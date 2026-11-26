package org.projectcontinuum.core.commons.validation

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel

/** The nodes the node checks accepted, by key in list order, and what the checks found. */
internal class CheckedNodes(val resolvedNodes: Map<String, ResolvedNode>, val issues: RuleIssues)

/**
 * The per-node checks of [WorkflowValidator]: a non-empty unique key that does not use the system prefix, a nodeId
 * that is registered, no reserved port ids in the registered definition (or in the submitted one, in
 * [ValidationMode.RUN]), a warning when the submitted definition differs from the registered one, and the properties
 * against the registered schema.
 *
 * Everything about a node comes from [descriptors], never from the submitted workflow. Each registered node type is
 * looked up once per call, however many nodes use it. One instance checks one workflow.
 */
internal class NodeChecks(private val descriptors: NodeDescriptorSource, private val mode: ValidationMode) {

  private val resolvedNodes = linkedMapOf<String, ResolvedNode>()
  private val registry = mutableMapOf<String, ContinuumWorkflowModel.NodeData?>()
  private val errors = mutableListOf<WorkflowIssue>()
  private val warnings = mutableListOf<WorkflowIssue>()

  fun check(nodes: List<GraphNode>): CheckedNodes {
    nodes.forEachIndexed { index, node -> checkNode(index = index, node = node) }
    return CheckedNodes(resolvedNodes = resolvedNodes, issues = RuleIssues(errors = errors, warnings = warnings))
  }

  private fun checkNode(index: Int, node: GraphNode) {
    val key = acceptedKey(index = index, node = node) ?: return
    // A reserved key is still accepted, so its edges do not also report "does not exist".
    ReservedIdRules.nodeKeyIssue(key = key)?.let { issue -> errors += issue }
    val nodeId = node.nodeId
    if (nodeId.isNullOrBlank()) {
      errors += WorkflowIssue(message = "Node '$key' needs a nodeId.", node = key)
      resolvedNodes[key] = ResolvedNode(key = key, nodeId = "", descriptor = null)
      return
    }
    val descriptor = registeredDefinition(nodeId = nodeId)
    if (descriptor == null) {
      errors += WorkflowIssue(message = unknownNodeMessage(key = key, nodeId = nodeId), node = key)
      resolvedNodes[key] = ResolvedNode(key = key, nodeId = nodeId, descriptor = null)
      return
    }
    checkRegisteredNode(key = key, nodeId = nodeId, node = node, descriptor = descriptor)
  }

  /** One lookup per node type, also when it is not registered (so `getOrPut`, which retries null values, is not used). */
  private fun registeredDefinition(nodeId: String): ContinuumWorkflowModel.NodeData? {
    if (nodeId !in registry) registry[nodeId] = descriptors.find(nodeId)
    return registry[nodeId]
  }

  /** The node's key when it is non-empty and not used before, else null after reporting why. */
  private fun acceptedKey(index: Int, node: GraphNode): String? {
    val key = node.key
    if (key.isNullOrBlank()) {
      errors += WorkflowIssue(message = "Node #${index + 1} needs a non-empty key.")
      return null
    }
    if (key in resolvedNodes) {
      errors += WorkflowIssue(message = "Node key '$key' is used more than once. Every node needs a unique key.", node = key)
      return null
    }
    return key
  }

  private fun checkRegisteredNode(key: String, nodeId: String, node: GraphNode, descriptor: ContinuumWorkflowModel.NodeData) {
    errors += ReservedIdRules.declaredPortIssues(key = key, definition = descriptor, source = "registered node '$nodeId'")
    node.embedded?.let { embedded ->
      if (mode == ValidationMode.RUN) {
        errors += ReservedIdRules.declaredPortIssues(key = key, definition = embedded, source = "submitted node")
      }
      embeddedDifferenceWarning(key = key, nodeId = nodeId, embedded = embedded, registered = descriptor)?.let { warning ->
        warnings += warning
      }
    }
    val given = node.properties.orEmpty()
    val propertyIssues = PropertyRules.issues(key = key, given = given, descriptor = descriptor, mode = mode)
    errors += propertyIssues
    resolvedNodes[key] = ResolvedNode(
      key = key,
      nodeId = nodeId,
      descriptor = descriptor,
      properties = given,
      propertyIssues = propertyIssues
    )
  }

  private fun unknownNodeMessage(key: String, nodeId: String) = when (mode) {
    ValidationMode.AUTHORING ->
      "Node '$key': no registered node with id '$nodeId'. Use the full node id exactly as returned by " +
        "searchContinuumNodes or listContinuumNodeCategories."
    ValidationMode.RUN ->
      "Node '$key': no registered node with id '$nodeId'. Its ports and properties cannot be checked, so the workflow " +
        "is not started. Check the id, or wait until the worker that provides this node has registered."
  }

  /**
   * A client-sent definition is client-controlled: a difference from the registered one is only a warning, and the
   * registered one is what gets validated.
   */
  private fun embeddedDifferenceWarning(
    key: String,
    nodeId: String,
    embedded: ContinuumWorkflowModel.NodeData,
    registered: ContinuumWorkflowModel.NodeData
  ): WorkflowIssue? {
    val differences = buildList {
      if (embedded.propertiesSchema != registered.propertiesSchema) add("propertiesSchema")
      if (embedded.inputs.orEmpty().keys != registered.inputs.orEmpty().keys) add("input ports")
      if (embedded.outputs.orEmpty().keys != registered.outputs.orEmpty().keys) add("output ports")
    }
    if (differences.isEmpty()) return null
    return WorkflowIssue(
      message = "Node '$key': the submitted ${differences.joinToString(" and ")} differ from the registered node '$nodeId'. " +
        "The registered definition was used for validation; reopen the workflow to refresh the node.",
      node = key
    )
  }
}
