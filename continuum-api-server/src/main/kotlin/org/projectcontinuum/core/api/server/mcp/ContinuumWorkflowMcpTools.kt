package org.projectcontinuum.core.api.server.mcp

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class ContinuumWorkflowMcpTools(
    private val workflowSpecValidator: WorkflowSpecValidator,
    private val workflowSpecBuilder: WorkflowSpecBuilder
) {

    @McpTool(
        name = "validateContinuumWorkflow",
        description = "Check a Continuum workflow against the live node registry before saving it. Takes the compact spec: " +
            "name, nodes [{key, nodeId, properties}] and edges [{from, fromPort, to, toPort}]. Checks that every nodeId is registered, " +
            "keys are unique, every edge goes from an existing output port to an existing input port, each input port has at most one edge, " +
            "there are no cycles, and each node's properties, merged over the node defaults, match its JSON Schema with no unknown keys " +
            "and every required value set. Returns valid=true, or valid=false with one error per problem naming the node, edge or property. " +
            "Fix every error and validate again. Warnings (such as an unconnected input port) do not make the workflow invalid."
    )
    fun validateContinuumWorkflow(
        @McpToolParam(description = "Workflow name", required = true)
        name: String?,
        @McpToolParam(description = "The nodes of the workflow: [{key, nodeId, properties}]", required = true)
        nodes: List<McpWorkflowSpecNode>?,
        @McpToolParam(description = "The connections: [{from, fromPort, to, toPort}]. Empty or omitted for a single node.", required = false)
        edges: List<McpWorkflowSpecEdge>?
    ): McpWorkflowValidationResult {
        return workflowSpecValidator.validate(name, nodes, edges)
    }

    @McpTool(
        name = "buildContinuumWorkflow",
        description = "Build the complete Continuum workflow (the content of a .cwf file) from the same compact spec that " +
            "validateContinuumWorkflow takes. The spec is validated first. If it has errors, returns valid=false with the errors and no workflow. " +
            "Otherwise returns valid=true, a suggested fileName and workflow: the full workflow JSON with every node's registry metadata, " +
            "the given properties merged over the defaults, edges and a layout. Save workflow unchanged as the file content; do not edit it. " +
            "An empty nodes list builds an empty workflow (with a warning), so a file can be saved first and rebuilt as nodes are added."
    )
    fun buildContinuumWorkflow(
        @McpToolParam(description = "Workflow name", required = true)
        name: String?,
        @McpToolParam(description = "The nodes of the workflow: [{key, nodeId, properties}]. Empty for an empty workflow.", required = true)
        nodes: List<McpWorkflowSpecNode>?,
        @McpToolParam(description = "The connections: [{from, fromPort, to, toPort}]. Empty or omitted for a single node.", required = false)
        edges: List<McpWorkflowSpecEdge>?
    ): McpWorkflowBuildResult {
        return workflowSpecBuilder.build(name, nodes, edges)
    }
}
