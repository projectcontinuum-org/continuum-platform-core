package org.projectcontinuum.core.api.server.mcp

import org.projectcontinuum.core.api.server.service.NodeExplorerService
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * Turns a compact workflow spec into a complete ContinuumWorkflowModel, the content of a .cwf file. The spec is checked
 * with WorkflowSpecValidator first; nothing is built when it has errors. Each node gets the registry NodeData with the
 * spec properties merged over the defaults, the React Flow fields the editor and API expect, and a left-to-right layout
 * by dependency depth.
 */
@Component
class WorkflowSpecBuilder(
    private val validator: WorkflowSpecValidator,
    private val nodeExplorerService: NodeExplorerService
) {

    companion object {
        const val NODE_WIDTH = 224
        const val NODE_HEIGHT = 100
        const val X_STEP = 330.0
        const val Y_STEP = 160.0
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
        val depths = depths(specNodes.map { it.key!! }, specEdges)
        val rowsUsed = mutableMapOf<Int, Int>()

        val workflowNodes = specNodes.map { node ->
            val key = node.key!!
            val depth = depths.getValue(key)
            val row = rowsUsed.merge(depth, 1, Int::plus)!! - 1
            val position = ContinuumWorkflowModel.Position(x = depth * X_STEP, y = row * Y_STEP)
            val data = metadata.getValue(node.nodeId!!)
            val properties = buildMap<String, Any> {
                putAll(data.properties)
                node.properties.orEmpty().forEach { (k, v) -> if (v == null) remove(k) else put(k, v) }
            }
            ContinuumWorkflowModel.Node(
                id = key,
                type = "BaseNode",
                position = position,
                data = data.copy(properties = properties),
                width = NODE_WIDTH,
                height = NODE_HEIGHT,
                selected = false,
                positionAbsolute = position,
                dragging = false
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

    /** Longest-path depth of every key from a root; the graph is known to be acyclic. */
    private fun depths(keys: List<String>, edges: List<McpWorkflowSpecEdge>): Map<String, Int> {
        val depth = keys.associateWith { 0 }.toMutableMap()
        val indegree = keys.associateWith { 0 }.toMutableMap()
        val outgoing = keys.associateWith { mutableListOf<String>() }
        for (edge in edges) {
            outgoing.getValue(edge.from!!).add(edge.to!!)
            indegree[edge.to] = indegree.getValue(edge.to) + 1
        }
        val queue = ArrayDeque(keys.filter { indegree[it] == 0 })
        while (queue.isNotEmpty()) {
            val key = queue.removeFirst()
            for (next in outgoing.getValue(key)) {
                depth[next] = maxOf(depth.getValue(next), depth.getValue(key) + 1)
                indegree[next] = indegree.getValue(next) - 1
                if (indegree[next] == 0) queue.addLast(next)
            }
        }
        return depth
    }
}
