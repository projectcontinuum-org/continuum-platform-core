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
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class KnimeWorkflowScheduleService(
  private val workflowScheduleApiClient: WorkflowScheduleApiClient,
  private val knimeWorkflowRepository: KnimeWorkflowRepository,
  @Value("\${continuum.core.knime-scheduler.public-base-url}") private val publicBaseUrl: String
) {

  fun createSchedule(request: CreateKnimeWorkflowScheduleRequest, ownedBy: String): KnimeWorkflowScheduleResponse {
    val workflow = knimeWorkflowRepository.findByWorkflowIdAndOwnedBy(request.knimeWorkflowId, ownedBy)
      ?: throw KnimeWorkflowNotFoundException(request.knimeWorkflowId)

    // Path ends in the workflow's actual file name (always .knwf, enforced at upload) because
    // KNIMEWorkflowExecutorNodeModel validates workflowLocation by file extension. The file name
    // is user-supplied and can contain spaces/special characters, so it must be percent-encoded
    // as a path segment before being embedded in the URL.
    val encodedFileName = UriUtils.encodePathSegment(workflow.fileName, StandardCharsets.UTF_8)
    val contentUrl = "$publicBaseUrl/api/v1/knime-workflows/${request.knimeWorkflowId}/content/$encodedFileName"
    val executionUploadUrl = "$publicBaseUrl/api/v1/knime-workflows/${request.knimeWorkflowId}/executions"
    val model = KnimeScheduleWorkflowMapper.toWorkflowModel(
      name = request.name,
      knimeWorkflowId = request.knimeWorkflowId,
      contentUrl = contentUrl,
      executionUploadUrl = executionUploadUrl,
      resetWorkflow = request.resetWorkflow,
      timeoutSeconds = request.timeoutSeconds,
      workflowVariables = request.workflowVariables,
      workflowCredentials = request.workflowCredentials
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
      timeoutSeconds = KnimeScheduleWorkflowMapper.extractTimeoutSeconds(response.continuumWorkflowModel),
      workflowVariables = KnimeScheduleWorkflowMapper.extractWorkflowVariables(response.continuumWorkflowModel),
      workflowCredentials = KnimeScheduleWorkflowMapper.extractWorkflowCredentials(response.continuumWorkflowModel)
    )
}
