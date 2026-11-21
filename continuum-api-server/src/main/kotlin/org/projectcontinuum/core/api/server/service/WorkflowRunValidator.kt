package org.projectcontinuum.core.api.server.service

import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.validation.GraphEdge
import org.projectcontinuum.core.commons.validation.GraphNode
import org.projectcontinuum.core.commons.validation.ValidationMode
import org.projectcontinuum.core.commons.validation.WorkflowIssue
import org.projectcontinuum.core.commons.validation.WorkflowValidationResult
import org.projectcontinuum.core.commons.validation.WorkflowValidator
import java.net.URI
import java.net.URISyntaxException

/**
 * The checks a workflow must pass before it runs, shared by every path that leads to a run: start
 * ([WorkflowService.startWorkflow]), dry run ([WorkflowService.validateWorkflow]) and schedule creation
 * ([WorkflowScheduleService.createSchedule]). Keep them on this one class so they cannot disagree.
 *
 * Runs the shared [WorkflowValidator] in [ValidationMode.RUN]. Ports and the properties schema come from the node
 * registry, never from the submitted workflow, whose copies are client-controlled. A submitted definition that differs
 * from the registered one is a warning, and the registered one decides. A node whose `nodeModel` is not registered is
 * an error: without its descriptor nothing about it can be checked, and a run that skipped the check would only fail
 * later on the worker.
 *
 * The workflow `name` is also checked: it becomes the run's `workflowUri`, so it must parse as a URI.
 */
class WorkflowRunValidator(nodeExplorerService: NodeExplorerService) {

  companion object {
    /** The issue for a workflow `name` that cannot be stored as a run's `workflowUri`, or null when it is fine. */
    fun workflowNameError(name: String): WorkflowIssue? {
      if (name.isBlank()) {
        return WorkflowIssue(message = "The workflow name must not be blank.")
      }
      return try {
        URI(name)
        null
      } catch (e: URISyntaxException) {
        WorkflowIssue(
          message = "The workflow name '$name' is not a valid URI path (${e.reason}). Use the workflow file path, " +
            "e.g. 'examples/text-cleanup.cwf', with spaces and other special characters percent-encoded."
        )
      }
    }
  }

  private val workflowValidator = WorkflowValidator { nodeModel -> nodeExplorerService.getNodeMetadata(nodeModel) }

  fun validate(workflow: ContinuumWorkflowModel): WorkflowValidationResult {
    val graphResult = workflowValidator.validate(
      nodes = workflow.nodes.map { node ->
        GraphNode(key = node.id, nodeId = node.data.nodeModel, properties = node.data.properties, embedded = node.data)
      },
      edges = workflow.edges.map { edge ->
        GraphEdge(from = edge.source, fromPort = edge.sourceHandle, to = edge.target, toPort = edge.targetHandle)
      },
      mode = ValidationMode.RUN
    )
    val nameError = workflowNameError(workflow.name) ?: return graphResult
    return graphResult.copy(errors = listOf(nameError) + graphResult.errors)
  }
}
