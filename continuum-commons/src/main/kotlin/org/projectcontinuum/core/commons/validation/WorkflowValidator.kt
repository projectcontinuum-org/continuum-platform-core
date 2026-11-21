package org.projectcontinuum.core.commons.validation

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.ValidationHelper

/** Where a node's registered definition (ports, properties schema, defaults) comes from. Implemented by the API server over the node registry. */
fun interface NodeDescriptorSource {
  /** The registered definition of [nodeModel], or null when no such node is registered. */
  fun find(nodeModel: String): ContinuumWorkflowModel.NodeData?
}

/**
 * How strict the property checks are, and how messages are worded.
 *
 * - [AUTHORING]: a workflow being built from a partial spec. Given properties are merged over the registered
 *   defaults, keys the schema does not declare are errors, and an empty required value counts as missing.
 * - [RUN]: a complete workflow about to be started. Properties are checked exactly as sent, the way the worker will
 *   check them, using the schema alone. Nothing the workbench or an old workflow stored is rejected for being extra
 *   unless the schema itself forbids it.
 */
enum class ValidationMode { AUTHORING, RUN }

/** A node to check. [key] identifies it in edges, [nodeId] is the registered node id, [embedded] is the definition the client sent (RUN only). */
data class GraphNode(
  val key: String?,
  val nodeId: String?,
  val properties: Map<String, Any?>? = null,
  val embedded: ContinuumWorkflowModel.NodeData? = null
)

data class GraphEdge(val from: String?, val fromPort: String?, val to: String?, val toPort: String?)

/** One problem found by [WorkflowValidator]. node, edge and property say where it is, when that applies. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "WorkflowIssue", description = "One problem found by the workflow validator. `node`, `edge` and `property` say where it is, when that applies; absent ones are omitted.")
data class WorkflowIssue(
  @field:Schema(description = "Human-readable description of the issue.", example = "Node 'normalize': property 'inputCol' is required.")
  val message: String,
  @field:Schema(description = "Id of the node concerned (`nodes[].id`).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "normalize")
  val node: String? = null,
  @field:Schema(description = "Id of the edge concerned (`edges[].id`).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "reactflow__edge-createdata-normalizedata")
  val edge: String? = null,
  @field:Schema(description = "Top-level node property concerned.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "inputCol")
  val property: String? = null
)

data class WorkflowValidationResult(val errors: List<WorkflowIssue>, val warnings: List<WorkflowIssue>) {
  val valid: Boolean get() = errors.isEmpty()
}

/**
 * The one workflow checker: unique node keys, registered node ids, edge endpoints and port names, no self or duplicate
 * edges, exactly one edge per input port, no cycles (Kahn's algorithm) and each node's properties against the
 * registered schema. Every problem is collected, not just the first, so a caller can fix them all in one pass.
 *
 * Everything about a node comes from [descriptors], never from the submitted workflow. A client-sent
 * `propertiesSchema` or port list is client-controlled; in [ValidationMode.RUN] a difference from the registered one
 * is a warning, and the registered one is what gets validated.
 *
 * Warnings (nodes with no edges, input ports with no incoming edge, client definitions that differ from the
 * registry) never make a workflow invalid. A node with an unconnected declared input is not an error: the
 * orchestrator marks it SKIPPED at run time.
 */
class WorkflowValidator(private val descriptors: NodeDescriptorSource) {

  private class ResolvedNode(val key: String, val nodeId: String, val descriptor: ContinuumWorkflowModel.NodeData?)

  fun validate(nodes: List<GraphNode>, edges: List<GraphEdge>, mode: ValidationMode): WorkflowValidationResult {
    val errors = mutableListOf<WorkflowIssue>()
    val warnings = mutableListOf<WorkflowIssue>()

    val byKey = validateNodes(nodes = nodes, mode = mode, errors = errors, warnings = warnings)
    val validEdges = validateEdges(edges = edges, byKey = byKey, errors = errors)

    val cycle = findCycle(keys = byKey.keys, edges = validEdges)
    if (cycle.isNotEmpty()) {
      errors += WorkflowIssue(
        message = "The edges contain a cycle through nodes ${cycle.joinToString(", ") { "'$it'" }}. A workflow must be a directed acyclic graph."
      )
    }

    collectConnectionWarnings(byKey = byKey, edges = validEdges, mode = mode, warnings = warnings)
    return WorkflowValidationResult(errors = errors, warnings = warnings)
  }

  private fun validateNodes(
    nodes: List<GraphNode>,
    mode: ValidationMode,
    errors: MutableList<WorkflowIssue>,
    warnings: MutableList<WorkflowIssue>
  ): Map<String, ResolvedNode> {
    val byKey = linkedMapOf<String, ResolvedNode>()
    // One registry lookup per node type, however many nodes use it.
    val registry = mutableMapOf<String, ContinuumWorkflowModel.NodeData?>()

    nodes.forEachIndexed { index, node ->
      val key = node.key
      if (key.isNullOrBlank()) {
        errors += WorkflowIssue(message = "Node #${index + 1} needs a non-empty key.")
        return@forEachIndexed
      }
      if (key in byKey) {
        errors += WorkflowIssue(message = "Node key '$key' is used more than once. Every node needs a unique key.", node = key)
        return@forEachIndexed
      }
      val nodeId = node.nodeId
      if (nodeId.isNullOrBlank()) {
        errors += WorkflowIssue(message = "Node '$key' needs a nodeId.", node = key)
        byKey[key] = ResolvedNode(key = key, nodeId = "", descriptor = null)
        return@forEachIndexed
      }
      if (nodeId !in registry) registry[nodeId] = descriptors.find(nodeId)
      val descriptor = registry[nodeId]
      byKey[key] = ResolvedNode(key = key, nodeId = nodeId, descriptor = descriptor)
      if (descriptor == null) {
        errors += WorkflowIssue(unknownNodeMessage(key = key, nodeId = nodeId, mode = mode), node = key)
        return@forEachIndexed
      }
      node.embedded?.let {
        warnOnEmbeddedDifference(
          key = key,
          nodeId = nodeId,
          embedded = it,
          registered = descriptor,
          warnings = warnings
        )
      }
      validateProperties(
        key = key,
        given = node.properties.orEmpty(),
        descriptor = descriptor,
        mode = mode,
        errors = errors
      )
    }
    return byKey
  }

  private fun unknownNodeMessage(key: String, nodeId: String, mode: ValidationMode) = when (mode) {
    ValidationMode.AUTHORING ->
      "Node '$key': no registered node with id '$nodeId'. Use the full node id exactly as returned by " +
        "searchContinuumNodes or listContinuumNodeCategories."
    ValidationMode.RUN ->
      "Node '$key': no registered node with id '$nodeId'. Its ports and properties cannot be checked, so the workflow " +
        "is not started. Check the id, or wait until the worker that provides this node has registered."
  }

  private fun warnOnEmbeddedDifference(
    key: String,
    nodeId: String,
    embedded: ContinuumWorkflowModel.NodeData,
    registered: ContinuumWorkflowModel.NodeData,
    warnings: MutableList<WorkflowIssue>
  ) {
    val differences = buildList {
      if (embedded.propertiesSchema != registered.propertiesSchema) add("propertiesSchema")
      if (embedded.inputs.orEmpty().keys != registered.inputs.orEmpty().keys) add("input ports")
      if (embedded.outputs.orEmpty().keys != registered.outputs.orEmpty().keys) add("output ports")
    }
    if (differences.isNotEmpty()) {
      warnings += WorkflowIssue(
        message = "Node '$key': the submitted ${differences.joinToString(" and ")} differ from the registered node '$nodeId'. " +
          "The registered definition was used for validation; reopen the workflow to refresh the node.",
        node = key
      )
    }
  }

  private fun validateProperties(
    key: String,
    given: Map<String, Any?>,
    descriptor: ContinuumWorkflowModel.NodeData,
    mode: ValidationMode,
    errors: MutableList<WorkflowIssue>
  ) {
    val schema = descriptor.propertiesSchema
    when (mode) {
      ValidationMode.AUTHORING -> {
        val declared = (schema["properties"] as? Map<*, *>)?.keys?.map { it.toString() }.orEmpty()
        for (property in given.keys - declared.toSet()) {
          errors += WorkflowIssue(
            message = "Node '$key': unknown property '$property'. Known properties: ${declared.joinToString(", ").ifEmpty { "(none)" }}.",
            node = key,
            property = property
          )
        }
        val merged: Map<String, Any?> = descriptor.properties + given
        val required = (schema["required"] as? List<*>)?.map { it.toString() }.orEmpty()
        for (property in required) {
          val value = merged[property]
          if (value == null || value == "") {
            errors += WorkflowIssue(
              message = "Node '$key': required property '$property' has no value. Set it in properties.",
              node = key,
              property = property
            )
          }
        }
        if (schema.isEmpty()) return
        // Unknown keys and missing required values are reported above, with clearer messages.
        schemaViolations(key = key, properties = merged, schema = schema, errors = errors) { it.keyword != "additionalProperties" && it.keyword != "required" }
      }
      ValidationMode.RUN -> {
        if (schema.isEmpty()) return
        schemaViolations(key = key, properties = given, schema = schema, errors = errors) { true }
      }
    }
  }

  private fun schemaViolations(
    key: String,
    properties: Map<String, Any?>,
    schema: Map<String, Any?>,
    errors: MutableList<WorkflowIssue>,
    keep: (ValidationHelper.SchemaViolation) -> Boolean
  ) {
    ValidationHelper.findSchemaViolations(properties, schema)
      .filter(keep)
      .forEach { violation ->
        errors += WorkflowIssue(
          message = "Node '$key' properties: ${violation.path.ifEmpty { "/" }}: ${violation.message}",
          node = key,
          property = violation.property
        )
      }
  }

  /** Returns the edges whose endpoints and ports all exist, so the cycle check and warnings only see real connections. */
  private fun validateEdges(
    edges: List<GraphEdge>,
    byKey: Map<String, ResolvedNode>,
    errors: MutableList<WorkflowIssue>
  ): List<GraphEdge> {
    val valid = mutableListOf<GraphEdge>()
    val seen = mutableSetOf<String>()
    val inputFedBy = mutableMapOf<String, String>()
    val knownKeys = byKey.keys.joinToString(", ").ifEmpty { "(none)" }

    edges.forEachIndexed { index, edge ->
      val label = "${edge.from}.${edge.fromPort} -> ${edge.to}.${edge.toPort}"
      if (edge.from.isNullOrBlank() || edge.fromPort.isNullOrBlank() || edge.to.isNullOrBlank() || edge.toPort.isNullOrBlank()) {
        errors += WorkflowIssue(message = "Edge #${index + 1} ($label) needs from, fromPort, to and toPort.", edge = label)
        return@forEachIndexed
      }
      var ok = true
      val source = byKey[edge.from]
      val target = byKey[edge.to]
      if (source == null) {
        errors += WorkflowIssue(message = "Edge $label: source node key '${edge.from}' does not exist. Node keys: $knownKeys.", edge = label)
        ok = false
      }
      if (target == null) {
        errors += WorkflowIssue(message = "Edge $label: target node key '${edge.to}' does not exist. Node keys: $knownKeys.", edge = label)
        ok = false
      }
      if (edge.from == edge.to) {
        errors += WorkflowIssue(message = "Edge $label: a node cannot connect to itself.", edge = label)
        ok = false
      }
      source?.descriptor?.let { descriptor ->
        val outputs = descriptor.outputs.orEmpty().keys
        if (edge.fromPort !in outputs) {
          errors += WorkflowIssue(
            message = "Edge $label: '${source.nodeId}' has no output port '${edge.fromPort}'. " +
              "Output ports: ${outputs.joinToString(", ").ifEmpty { "(none)" }}.",
            edge = label
          )
          ok = false
        }
      }
      target?.descriptor?.let { descriptor ->
        val inputs = descriptor.inputs.orEmpty().keys
        if (edge.toPort !in inputs) {
          errors += WorkflowIssue(
            message = "Edge $label: '${target.nodeId}' has no input port '${edge.toPort}'. " +
              "Input ports: ${inputs.joinToString(", ").ifEmpty { "(none)" }}.",
            edge = label
          )
          ok = false
        }
      }
      if (!seen.add(label)) {
        errors += WorkflowIssue(message = "Edge $label is listed more than once.", edge = label)
        return@forEachIndexed
      }
      val input = "${edge.to}.${edge.toPort}"
      val previous = inputFedBy.putIfAbsent(input, "${edge.from}.${edge.fromPort}")
      if (previous != null) {
        errors += WorkflowIssue(
          message = "Edge $label: input port '${edge.toPort}' of node '${edge.to}' is already fed by $previous. " +
            "Each input port takes exactly one edge.",
          edge = label
        )
        ok = false
      }
      if (ok) valid += edge
    }
    return valid
  }

  /** Kahn's algorithm; returns the keys left over when the graph has a cycle, else an empty list. */
  private fun findCycle(keys: Set<String>, edges: List<GraphEdge>): List<String> {
    val indegree = keys.associateWith { 0 }.toMutableMap()
    val outgoing = keys.associateWith { mutableListOf<String>() }
    for (edge in edges) {
      outgoing.getValue(edge.from!!).add(edge.to!!)
      indegree[edge.to] = indegree.getValue(edge.to) + 1
    }
    val queue = ArrayDeque(indegree.filterValues { it == 0 }.keys)
    while (queue.isNotEmpty()) {
      for (next in outgoing.getValue(queue.removeFirst())) {
        indegree[next] = indegree.getValue(next) - 1
        if (indegree[next] == 0) queue.addLast(next)
      }
    }
    return indegree.filterValues { it > 0 }.keys.toList()
  }

  private fun collectConnectionWarnings(
    byKey: Map<String, ResolvedNode>,
    edges: List<GraphEdge>,
    mode: ValidationMode,
    warnings: MutableList<WorkflowIssue>
  ) {
    for (node in byKey.values) {
      val connected = edges.any { it.from == node.key || it.to == node.key }
      if (!connected && byKey.size > 1) {
        warnings += WorkflowIssue(message = "Node '${node.key}' is not connected to any other node.", node = node.key)
      }
      val fed = edges.filter { it.to == node.key }.map { it.toPort }.toSet()
      for (port in node.descriptor?.inputs.orEmpty().keys - fed) {
        val consequence = when (mode) {
          ValidationMode.AUTHORING -> "The node may fail if it needs this input."
          ValidationMode.RUN -> "The node is skipped at run time, so nodes that depend on it do not run."
        }
        warnings += WorkflowIssue(message = "Node '${node.key}': input port '$port' has no incoming edge. $consequence", node = node.key)
      }
    }
  }
}
