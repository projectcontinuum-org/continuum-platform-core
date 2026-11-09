package org.projectcontinuum.core.api.server.mcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.ai.mcp.annotation.McpToolParam

/**
 * One node of the compact workflow spec accepted by validateContinuumWorkflow and buildContinuumWorkflow. Fields are
 * nullable so that a missing value is reported as a validation error instead of failing argument conversion.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class McpWorkflowSpecNode(
    @field:McpToolParam(description = "Unique key of this node within the workflow, used by edges, e.g. 'reader'", required = true)
    val key: String? = null,
    @field:McpToolParam(
        description = "Full registered node id, e.g. 'org.projectcontinuum.feature.rdkit.node.RDKit2SVGNodeModel'",
        required = true
    )
    val nodeId: String? = null,
    @field:McpToolParam(
        description = "Property values to set. Keys must exist in the node's propertiesSchema; unset properties keep the node defaults.",
        required = false
    )
    val properties: Map<String, Any?>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class McpWorkflowSpecEdge(
    @field:McpToolParam(description = "Key of the source node", required = true)
    val from: String? = null,
    @field:McpToolParam(description = "Output port name on the source node", required = true)
    val fromPort: String? = null,
    @field:McpToolParam(description = "Key of the target node", required = true)
    val to: String? = null,
    @field:McpToolParam(description = "Input port name on the target node", required = true)
    val toPort: String? = null
)

/** One problem found by validateContinuumWorkflow. node, edge and property say where it is, when that applies. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class McpWorkflowIssue(
    val message: String,
    val node: String? = null,
    val edge: String? = null,
    val property: String? = null
)

data class McpWorkflowValidationResult(
    val valid: Boolean,
    val nodeCount: Int,
    val edgeCount: Int,
    val errors: List<McpWorkflowIssue>,
    val warnings: List<McpWorkflowIssue>
)

/**
 * Result of buildContinuumWorkflow. When valid, workflow is the complete .cwf content and fileName a suggested file name;
 * otherwise both are absent and errors says what to fix. workflow comes last so a client reads the verdict first.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder("valid", "fileName", "errors", "warnings", "workflow")
data class McpWorkflowBuildResult(
    val valid: Boolean,
    val fileName: String? = null,
    val errors: List<McpWorkflowIssue>,
    val warnings: List<McpWorkflowIssue>,
    val workflow: ContinuumWorkflowModel? = null
)
