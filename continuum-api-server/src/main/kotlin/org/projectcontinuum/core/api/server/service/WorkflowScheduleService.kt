package org.projectcontinuum.core.api.server.service

import com.google.protobuf.ByteString
import io.grpc.Status
import io.temporal.api.common.v1.Payload
import io.temporal.client.WorkflowOptions
import io.temporal.client.schedules.Schedule
import io.temporal.client.schedules.ScheduleActionStartWorkflow
import io.temporal.client.schedules.ScheduleClient
import io.temporal.client.schedules.ScheduleDescription
import io.temporal.client.schedules.ScheduleException
import io.temporal.client.schedules.ScheduleOptions
import io.temporal.client.schedules.ScheduleSpec
import io.temporal.common.interceptors.Header
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowScheduleEntity
import org.projectcontinuum.core.api.server.exception.TemporalErrors
import org.projectcontinuum.core.api.server.exception.WorkflowValidationException
import org.projectcontinuum.core.api.server.exception.WorkflowScheduleNotFoundException
import org.projectcontinuum.core.api.server.model.CreateWorkflowScheduleRequest
import org.projectcontinuum.core.api.server.model.WorkflowScheduleResponse
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowScheduleRepository
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.context.ContinuumOwnerContext
import org.projectcontinuum.core.commons.context.ContinuumScheduleContext
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WorkflowScheduleService(
  private val scheduleClient: ScheduleClient,
  private val workflowScheduleRepository: WorkflowScheduleRepository,
  nodeExplorerService: NodeExplorerService
) {

  private val workflowRunValidator = WorkflowRunValidator(nodeExplorerService)

  /**
   * Runs [action] against the Temporal schedule [scheduleId]. A schedule that exists in the database but no longer in
   * Temporal (deleted there directly) is the same 404 as an unknown one; every other failure propagates.
   */
  private fun <T> onTemporalSchedule(scheduleId: UUID, action: () -> T): T =
    try {
      action()
    } catch (e: ScheduleException) {
      if (TemporalErrors.grpcStatusCode(e) == Status.Code.NOT_FOUND) {
        throw WorkflowScheduleNotFoundException("Schedule $scheduleId not found in Temporal")
      }
      throw e
    }

  fun createSchedule(request: CreateWorkflowScheduleRequest, ownedBy: String): WorkflowScheduleResponse {
    // The same checks as a start (graph, registered node properties, workflow name), before Temporal or the database.
    val validation = workflowRunValidator.validate(request.continuumWorkflowModel)
    if (!validation.valid) {
      throw WorkflowValidationException(errors = validation.errors, warnings = validation.warnings)
    }

    val scheduleId = UUID.randomUUID()

    val header = Header(
      mapOf(
        ContinuumOwnerContext.HEADER_KEY to Payload.newBuilder()
          .setData(ByteString.copyFromUtf8(ownedBy))
          .build(),
        ContinuumScheduleContext.HEADER_KEY to Payload.newBuilder()
          .setData(ByteString.copyFromUtf8(scheduleId.toString()))
          .build()
      )
    )

    val action = ScheduleActionStartWorkflow.newBuilder()
      .setWorkflowType(IContinuumWorkflow::class.java)
      .setArguments(request.continuumWorkflowModel)
      .setOptions(
        WorkflowOptions.newBuilder()
          .setWorkflowId(scheduleId.toString())
          .setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE)
          .build()
      )
      .setHeader(header)
      .build()

    val spec = ScheduleSpec.newBuilder()
      .setCronExpressions(listOf(request.cronExpression))
      .apply { request.timeZone?.let { setTimeZoneName(it) } }
      .build()

    val schedule = Schedule.newBuilder()
      .setAction(action)
      .setSpec(spec)
      .build()

    // Let ScheduleException/ScheduleAlreadyRunningException propagate to the controller.
    // Create the Temporal schedule before persisting so a Temporal-side failure never
    // orphans a DB row.
    scheduleClient.createSchedule(scheduleId.toString(), schedule, ScheduleOptions.newBuilder().build())

    val entity = workflowScheduleRepository.save(
      WorkflowScheduleEntity(
        scheduleId = scheduleId,
        name = request.name,
        ownedBy = ownedBy,
        cronExpression = request.cronExpression,
        timeZone = request.timeZone,
        workflow = request.continuumWorkflowModel
      )
    )

    return toResponse(entity, scheduleClient.getHandle(scheduleId.toString()).describe())
  }

  fun listSchedules(ownedBy: String, name: String? = null): List<WorkflowScheduleResponse> {
    val entities = if (name != null) workflowScheduleRepository.findByOwnedByAndName(ownedBy, name)
                   else workflowScheduleRepository.findByOwnedBy(ownedBy)
    return entities.map { entity ->
      toResponse(entity, scheduleClient.getHandle(entity.scheduleId.toString()).describe())
    }
  }

  fun getSchedule(ownedBy: String, scheduleId: UUID): WorkflowScheduleResponse? {
    val entity = findOwnedEntity(ownedBy, scheduleId) ?: return null
    val description = onTemporalSchedule(scheduleId) { scheduleClient.getHandle(scheduleId.toString()).describe() }
    return toResponse(entity, description)
  }

  fun pauseSchedule(ownedBy: String, scheduleId: UUID, note: String?) {
    requireOwned(ownedBy, scheduleId)
    val handle = scheduleClient.getHandle(scheduleId.toString())
    onTemporalSchedule(scheduleId) { if (note != null) handle.pause(note) else handle.pause() }
  }

  fun unpauseSchedule(ownedBy: String, scheduleId: UUID, note: String?) {
    requireOwned(ownedBy, scheduleId)
    val handle = scheduleClient.getHandle(scheduleId.toString())
    onTemporalSchedule(scheduleId) { if (note != null) handle.unpause(note) else handle.unpause() }
  }

  fun triggerNow(ownedBy: String, scheduleId: UUID) {
    requireOwned(ownedBy, scheduleId)
    onTemporalSchedule(scheduleId) { scheduleClient.getHandle(scheduleId.toString()).trigger() }
  }

  fun deleteSchedule(ownedBy: String, scheduleId: UUID) {
    requireOwned(ownedBy, scheduleId)
    onTemporalSchedule(scheduleId) { scheduleClient.getHandle(scheduleId.toString()).delete() }
    workflowScheduleRepository.deleteById(scheduleId)
  }

  private fun findOwnedEntity(ownedBy: String, scheduleId: UUID): WorkflowScheduleEntity? =
    workflowScheduleRepository.findById(scheduleId).orElse(null)?.takeIf { it.ownedBy == ownedBy }

  private fun requireOwned(ownedBy: String, scheduleId: UUID) {
    if (findOwnedEntity(ownedBy, scheduleId) == null) {
      throw WorkflowScheduleNotFoundException("Schedule $scheduleId not found for owner $ownedBy")
    }
  }

  private fun toResponse(entity: WorkflowScheduleEntity, description: ScheduleDescription): WorkflowScheduleResponse =
    WorkflowScheduleResponse(
      scheduleId = entity.scheduleId,
      name = entity.name,
      ownedBy = entity.ownedBy,
      cronExpression = entity.cronExpression,
      timeZone = entity.timeZone,
      paused = description.schedule.state?.isPaused ?: false,
      nextRunTimes = description.info.nextActionTimes,
      createdAt = entity.createdAt,
      updatedAt = entity.updatedAt,
      continuumWorkflowModel = entity.workflow
    )
}
