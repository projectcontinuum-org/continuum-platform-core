package org.projectcontinuum.core.api.server.service

import org.projectcontinuum.core.api.server.exception.WorkflowValidationException
import org.projectcontinuum.core.api.server.model.StartWorkflowResponse
import org.projectcontinuum.core.api.server.model.WorkflowValidationResponse
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.ExecutionStatus
import org.projectcontinuum.core.commons.validation.WorkflowIssue
import org.projectcontinuum.core.commons.validation.WorkflowValidationResult
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowNotFoundException
import io.temporal.client.WorkflowOptions
import io.temporal.common.SearchAttributes
import org.projectcontinuum.core.api.server.model.WorkflowRunData
import org.projectcontinuum.core.commons.context.ContinuumOwnerContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Instant
import java.util.UUID

@Service
@EnableScheduling
class WorkflowService(
  val workflowClient: WorkflowClient,
  val workflowRunRepository: WorkflowRunRepository,
  nodeExplorerService: NodeExplorerService,
  private val stopScheduler: WorkflowStopScheduler
) {

  private val workflowRunValidator = WorkflowRunValidator(nodeExplorerService)

  companion object {
    private val LOGGER = LoggerFactory.getLogger(WorkflowService::class.java)

    /** How long a run gets to handle its stop signal before Temporal stops it. */
    const val STOP_SIGNAL_GRACE_PERIOD_MS = 5000L
  }

  fun startWorkflow(
    continuumWorkflowModel: ContinuumWorkflowModel,
    ownedBy: String
  ): StartWorkflowResponse {

    // An invalid graph or node properties (checked against the registry) are a 400, before any row or Temporal workflow exists.
    val warnings = requireValidWorkflow(continuumWorkflowModel)
    if (warnings.isNotEmpty()) {
      LOGGER.info(
        "Starting workflow '{}' with {} validation warning(s): {}",
        continuumWorkflowModel.name,
        warnings.size,
        warnings.map { it.message }
      )
    }

    val workflowId = UUID.randomUUID().toString()

    val continuumWorkflow = workflowClient.newWorkflowStub(
      IContinuumWorkflow::class.java,
      WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE)
        .setTypedSearchAttributes(
          SearchAttributes.newBuilder()
            .set(IContinuumWorkflow.WORKFLOW_FILE_PATH, continuumWorkflowModel.name)
            .set(IContinuumWorkflow.WORKFLOW_STATUS, ExecutionStatus.UNKNOWN.value)
            .build()
        )
        .build()
    )

    // Save workflow run to database before starting Temporal execution, so the owner row exists before the first
    // event reaches the message-bridge. It is inserted here and not after the start: once the workflow is running
    // the bridge may already have upserted newer status/outputs, and a later save() would overwrite them.
    workflowRunRepository.save(
      WorkflowRunEntity(
        workflowId = workflowId,
        workflowType = IContinuumWorkflow::class.simpleName!!,
        ownedBy = ownedBy,
        workflowUri = URI.create(continuumWorkflowModel.name),
        progressPercentage = 0,
        status = "PENDING",
        data = WorkflowRunData(
          workflowSnapshot = continuumWorkflowModel,
          nodeToOutputMap = emptyMap()
        )
      )
    )

    // Set the owner context so ContinuumContextPropagator carries it into Temporal headers
    ContinuumOwnerContext.set(ownedBy)
    val workflowExecution: WorkflowExecution = try {
      WorkflowClient.start(
        continuumWorkflow::start,
        continuumWorkflowModel
      )
    } catch (e: Exception) {
      // The workflow never started, so the PENDING row would be an orphan that no event will ever update.
      runCatching { workflowRunRepository.deleteById(workflowId) }
        .onFailure { LOGGER.warn("Could not remove the run row of workflow $workflowId after a failed start", it) }
      throw e
    } finally {
      ContinuumOwnerContext.clear()
    }

    recordRunId(workflowId, workflowExecution.runId)
    return StartWorkflowResponse(workflowId = workflowExecution.workflowId, warnings = warnings)
  }

  /**
   * Checks [workflow] exactly as a start would, without starting anything: no run row, no Temporal call. Invalid is a
   * normal result here, so the workbench can show every error and warning while the user is still editing.
   *
   * [ownedBy] is the caller. The static checks do not use it yet; it is in the signature for the planned partial run,
   * which will execute part of the workflow on the caller's behalf to check that properties line up.
   */
  @Suppress("UNUSED_PARAMETER")
  fun validateWorkflow(workflow: ContinuumWorkflowModel, ownedBy: String): WorkflowValidationResponse {
    val result = runValidation(workflow)
    return WorkflowValidationResponse(
      valid = result.valid,
      nodeCount = workflow.nodes.size,
      edgeCount = workflow.edges.size,
      errors = result.errors,
      warnings = result.warnings
    )
  }

  /** Returns the warnings, or throws [WorkflowValidationException] (HTTP 400) carrying every error. */
  private fun requireValidWorkflow(workflow: ContinuumWorkflowModel): List<WorkflowIssue> {
    val result = runValidation(workflow)
    if (!result.valid) throw WorkflowValidationException(errors = result.errors, warnings = result.warnings)
    return result.warnings
  }

  /** The checks of [WorkflowRunValidator], shared with schedule creation so start, validate and schedule agree. */
  private fun runValidation(workflow: ContinuumWorkflowModel): WorkflowValidationResult =
    workflowRunValidator.validate(workflow)

  /**
   * Stores the Temporal run id, which node outputs are stored under, as soon as the workflow has started. Only
   * `run_id` is updated. A failure here must not fail the start (the workflow is already running): the
   * message-bridge also writes the run id from the first workflow event, so this only closes the gap before it.
   */
  private fun recordRunId(workflowId: String, runId: String?) {
    if (runId.isNullOrBlank()) {
      LOGGER.warn("Temporal returned no run id for workflow $workflowId; the message-bridge will record it")
      return
    }
    try {
      workflowRunRepository.updateRunId(workflowId, runId)
    } catch (e: Exception) {
      LOGGER.warn("Could not record run id $runId for workflow $workflowId; the message-bridge will record it", e)
    }
  }

  /**
   * Throws [WorkflowNotFoundException] unless [ownedBy] owns the run. Unowned and nonexistent ids are treated
   * identically so a tenant cannot probe for other tenants' workflow ids. This must run before any signal is sent.
   */
  private fun requireOwned(ownedBy: String, workflowId: String) {
    if (!workflowRunRepository.existsByWorkflowIdAndOwnedBy(workflowId, ownedBy)) {
      throw WorkflowNotFoundException(WorkflowExecution.newBuilder().setWorkflowId(workflowId).build(), null, null)
    }
  }

  fun cancelWorkflow(ownedBy: String, workflowId: String, reason: String?) {
    requireOwned(ownedBy, workflowId)
    val stub = workflowClient.newWorkflowStub(IContinuumWorkflow::class.java, workflowId)
    try {
      stub.notifyCancelling(reason)
    } catch (e: WorkflowNotFoundException) {
      throw e
    } catch (e: Exception) {
      LOGGER.warn("Failed to signal notifyCancelling for workflow $workflowId, proceeding with cancel anyway", e)
    }
    // Give the workflow a chance to process the signal before the hard-stop is issued.
    stopScheduler.schedule(delayMillis = STOP_SIGNAL_GRACE_PERIOD_MS) {
      try {
        workflowClient.newUntypedWorkflowStub(workflowId).cancel()
      } catch (e: Exception) {
        LOGGER.warn("Failed to cancel workflow $workflowId", e)
      }
    }
  }

  fun terminateWorkflow(ownedBy: String, workflowId: String, reason: String?) {
    requireOwned(ownedBy, workflowId)
    val stub = workflowClient.newWorkflowStub(IContinuumWorkflow::class.java, workflowId)
    try {
      stub.notifyTerminating(reason)
    } catch (e: WorkflowNotFoundException) {
      throw e
    } catch (e: Exception) {
      LOGGER.warn("Failed to signal notifyTerminating for workflow $workflowId, proceeding with terminate anyway", e)
    }
    // Give the workflow a chance to process the signal before the hard-stop is issued.
    // This matters most for terminate() since it kills the workflow with no chance to
    // run any code afterward.
    stopScheduler.schedule(delayMillis = STOP_SIGNAL_GRACE_PERIOD_MS) {
      terminateAndRecord(ownedBy = ownedBy, workflowId = workflowId, reason = reason)
    }
  }

  /**
   * Terminates the run in Temporal and then records `TERMINATED` on its run row. The run's own signal handler
   * publishes that status normally, but a run that keeps retrying a workflow task (for example a loop check that
   * waits for an evaluation thread, retried minutes apart) runs no code before the hard stop, so without this its
   * record would say `RUNNING` for ever. A run that already ended (Temporal has nothing to terminate) keeps the status
   * it ended with, and a row that is already `TERMINATED` is not written again.
   */
  private fun terminateAndRecord(ownedBy: String, workflowId: String, reason: String?) {
    try {
      workflowClient.newUntypedWorkflowStub(workflowId).terminate(reason ?: "Terminated via API")
    } catch (e: WorkflowNotFoundException) {
      LOGGER.info("Workflow $workflowId ended before it could be terminated; its run record keeps its final status")
      return
    } catch (e: Exception) {
      LOGGER.warn("Failed to terminate workflow $workflowId", e)
      return
    }
    val changedRows = workflowRunRepository.markTerminated(
      workflowId = workflowId,
      ownedBy = ownedBy,
      terminatedAt = Instant.now()
    )
    if (changedRows == 0) {
      LOGGER.debug("The run record of workflow {} already says TERMINATED", workflowId)
    }
  }
}
