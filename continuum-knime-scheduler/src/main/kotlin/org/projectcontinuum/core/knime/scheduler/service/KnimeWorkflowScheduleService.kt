package org.projectcontinuum.core.knime.scheduler.service

import org.projectcontinuum.core.knime.scheduler.client.CreateWorkflowScheduleApiRequest
import org.projectcontinuum.core.knime.scheduler.client.WorkflowScheduleApiClient
import org.projectcontinuum.core.knime.scheduler.client.WorkflowScheduleApiResponse
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowNotFoundException
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowScheduleNotFoundException
import org.projectcontinuum.core.knime.scheduler.model.CreateKnimeWorkflowScheduleRequest
import org.projectcontinuum.core.knime.scheduler.model.KnimeWorkflowScheduleResponse
import org.projectcontinuum.core.knime.scheduler.repository.KnimeWorkflowRepository
import org.projectcontinuum.core.knime.scheduler.util.KnimeScheduleWorkflowMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class KnimeWorkflowScheduleService(
  private val workflowScheduleApiClient: WorkflowScheduleApiClient,
  private val knimeWorkflowRepository: KnimeWorkflowRepository,
  @Value("\${continuum.core.knime-scheduler.public-base-url}") private val publicBaseUrl: String
) {

  fun createSchedule(request: CreateKnimeWorkflowScheduleRequest, ownedBy: String): KnimeWorkflowScheduleResponse {
    knimeWorkflowRepository.findByWorkflowIdAndOwnedBy(request.knimeWorkflowId, ownedBy)
      ?: throw KnimeWorkflowNotFoundException(request.knimeWorkflowId)

    val contentUrl = "$publicBaseUrl/api/v1/knime-workflows/${request.knimeWorkflowId}/content"
    val model = KnimeScheduleWorkflowMapper.toWorkflowModel(
      name = request.name,
      knimeWorkflowId = request.knimeWorkflowId,
      contentUrl = contentUrl,
      resetWorkflow = request.resetWorkflow,
      timeoutSeconds = request.timeoutSeconds
    )

    val response = workflowScheduleApiClient.createSchedule(
      CreateWorkflowScheduleApiRequest(
        name = request.name,
        cronExpression = request.cronExpression,
        timeZone = request.timeZone,
        continuumWorkflowModel = model
      ),
      ownedBy
    )
    return toKnimeResponse(response)
  }

  fun listSchedules(ownedBy: String): List<KnimeWorkflowScheduleResponse> =
    workflowScheduleApiClient.listSchedules(ownedBy)
      .filter { KnimeScheduleWorkflowMapper.isKnimeExecutorWorkflow(it.continuumWorkflowModel) }
      .map { toKnimeResponse(it) }

  fun getSchedule(ownedBy: String, scheduleId: UUID): KnimeWorkflowScheduleResponse {
    val response = workflowScheduleApiClient.getSchedule(ownedBy, scheduleId)
      ?.takeIf { KnimeScheduleWorkflowMapper.isKnimeExecutorWorkflow(it.continuumWorkflowModel) }
      ?: throw notFound(scheduleId, ownedBy)
    return toKnimeResponse(response)
  }

  fun pauseSchedule(ownedBy: String, scheduleId: UUID, note: String?) {
    requireKnimeSchedule(ownedBy, scheduleId)
    workflowScheduleApiClient.pauseSchedule(ownedBy, scheduleId, note)
  }

  fun unpauseSchedule(ownedBy: String, scheduleId: UUID, note: String?) {
    requireKnimeSchedule(ownedBy, scheduleId)
    workflowScheduleApiClient.unpauseSchedule(ownedBy, scheduleId, note)
  }

  fun triggerNow(ownedBy: String, scheduleId: UUID) {
    requireKnimeSchedule(ownedBy, scheduleId)
    workflowScheduleApiClient.triggerNow(ownedBy, scheduleId)
  }

  fun deleteSchedule(ownedBy: String, scheduleId: UUID) {
    requireKnimeSchedule(ownedBy, scheduleId)
    workflowScheduleApiClient.deleteSchedule(ownedBy, scheduleId)
  }

  private fun requireKnimeSchedule(ownedBy: String, scheduleId: UUID) {
    workflowScheduleApiClient.getSchedule(ownedBy, scheduleId)
      ?.takeIf { KnimeScheduleWorkflowMapper.isKnimeExecutorWorkflow(it.continuumWorkflowModel) }
      ?: throw notFound(scheduleId, ownedBy)
  }

  private fun notFound(scheduleId: UUID, ownedBy: String) =
    KnimeWorkflowScheduleNotFoundException("Schedule $scheduleId not found for owner $ownedBy")

  private fun toKnimeResponse(response: WorkflowScheduleApiResponse): KnimeWorkflowScheduleResponse =
    KnimeWorkflowScheduleResponse(
      scheduleId = response.scheduleId,
      name = response.name,
      ownedBy = response.ownedBy,
      cronExpression = response.cronExpression,
      timeZone = response.timeZone,
      paused = response.paused,
      nextRunTimes = response.nextRunTimes,
      createdAt = response.createdAt,
      updatedAt = response.updatedAt,
      knimeWorkflowId = KnimeScheduleWorkflowMapper.extractKnimeWorkflowId(response.continuumWorkflowModel)
        ?: response.scheduleId,
      resetWorkflow = KnimeScheduleWorkflowMapper.extractResetWorkflow(response.continuumWorkflowModel),
      timeoutSeconds = KnimeScheduleWorkflowMapper.extractTimeoutSeconds(response.continuumWorkflowModel)
    )
}
