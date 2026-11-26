package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * Turns a compact workflow spec into a complete ContinuumWorkflowModel, the content of a .cwf file. The spec is checked
 * with WorkflowSpecValidator first; nothing is built when it has errors. Each node gets the registry NodeData with the
 * spec properties merged over the defaults, the React Flow fields the editor and API expect, and a left-to-right layout
 * by dependency depth ([WorkflowSpecLayout]). A loop (the built-in loop nodeId) becomes a box of type `Loop` that holds
 * the nodes whose `parent` names it; it is listed before them, and their positions are relative to it.
 */
@Component
class WorkflowSpecBuilder(
    private val validator: WorkflowSpecValidator,
    private val nodeExplorerService: NodeExplorerService
) {

    companion object {
        const val NODE_WIDTH = WorkflowSpecLayout.NODE_WIDTH
        const val NODE_HEIGHT = WorkflowSpecLayout.NODE_HEIGHT
        const val X_STEP = WorkflowSpecLayout.X_STEP
        const val Y_STEP = WorkflowSpecLayout.Y_STEP
        private val random = SecureRandom()

        fun fileName(name: String): String {
            val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60).trim('-')
            return "${slug.ifEmpty { "workflow" }}.cwf"
        }

        private fun randomId(): String {
            val bytes = ByteArray(32).also { random.nextBytes(it) }
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    fun build(name: String?, nodes: List<McpWorkflowSpecNode>?, edges: List<McpWorkflowSpecEdge>?): McpWorkflowBuildResult {
        val validation = validator.validate(name, nodes, edges)
        if (!validation.valid) {
            return McpWorkflowBuildResult(valid = false, errors = validation.errors, warnings = validation.warnings)
        }
        // A valid spec has a name, non-blank unique keys, registered node ids and edges with all four fields set.
        // It may have no nodes: that builds an empty workflow.
        val specNodes = nodes.orEmpty()
        val specEdges = edges.orEmpty()
        val metadata = specNodes.map { it.nodeId!! }.distinct().associateWith { nodeExplorerService.getNodeMetadata(it)!! }
        val specNodesByKey = specNodes.associateBy { it.key!! }
        val layout = WorkflowSpecLayout(
            keys = specNodes.map { it.key!! },
            parentByKey = specNodes.mapNotNull { node -> node.parent?.let { parent -> node.key!! to parent } }.toMap(),
            loopKeys = specNodes.filter { node -> LoopNodeSpec.isLoop(nodeId = node.nodeId) }.map { it.key!! }.toSet(),
            edges = specEdges.map { edge -> edge.from!! to edge.to!! }
        ).compute()

        // Parent-first order: React Flow needs every loop before its members.
        val workflowNodes = layout.orderedKeys.map { key ->
            workflowNode(
                specNode = specNodesByKey.getValue(key),
                registered = metadata.getValue(specNodesByKey.getValue(key).nodeId!!),
                placement = layout.placements.getValue(key)
            )
        }
        val workflowEdges = specEdges.map { edge ->
            ContinuumWorkflowModel.Edge(
                id = "reactflow__edge-${edge.from}${edge.fromPort}-${edge.to}${edge.toPort}",
                type = "BaseEdge",
                source = edge.from!!,
                target = edge.to!!,
                sourceHandle = edge.fromPort!!,
                targetHandle = edge.toPort!!
            )
        }
        val workflowName = name!!.trim()
        return McpWorkflowBuildResult(
            valid = true,
            fileName = fileName(workflowName),
            errors = emptyList(),
            warnings = validation.warnings,
            workflow = ContinuumWorkflowModel(
                id = randomId(),
                name = workflowName,
                active = false,
                nodes = workflowNodes,
                edges = workflowEdges
            )
        )
    }

    /**
     * One node of the built workflow: the registry [registered] data with the spec properties merged over the
     * defaults (a null value removes the default). A loop gets type `Loop`, its box size and `collapsed = false`; a
     * member gets `parentNode` and a position relative to its loop.
     */
    private fun workflowNode(
        specNode: McpWorkflowSpecNode,
        registered: ContinuumWorkflowModel.NodeData,
        placement: WorkflowSpecLayout.Placement
    ): ContinuumWorkflowModel.Node {
        val properties = buildMap<String, Any> {
            putAll(registered.properties)
            specNode.properties.orEmpty().forEach { (k, v) -> if (v == null) remove(k) else put(k, v) }
        }
        val isLoop = LoopNodeSpec.isLoop(nodeId = specNode.nodeId)
        val data = if (isLoop) {
            registered.copy(properties = properties, collapsed = false)
        } else {
            registered.copy(properties = properties)
        }
        return ContinuumWorkflowModel.Node(
            id = specNode.key!!,
            type = if (isLoop) LoopNode.NODE_TYPE else "BaseNode",
            position = placement.position,
            data = data,
            width = placement.width,
            height = placement.height,
            selected = false,
            positionAbsolute = placement.absolutePosition,
            dragging = false,
            parentNode = specNode.parent
        )
    }
}
