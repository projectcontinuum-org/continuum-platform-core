package org.projectcontinuum.core.knime.scheduler.client

import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowScheduleNotFoundException
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowScheduleRequestInvalidException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Instant
import java.util.UUID

private const val USER_ID_HEADER = "x-continuum-user-id"

data class Position(val x: Double, val y: Double)

data class WorkflowNodeData(
  val title: String,
  val description: String,
  val nodeModel: String,
  val properties: Map<String, Any> = emptyMap(),
  val propertiesSchema: Map<String, Any> = emptyMap()
)

data class WorkflowNode(
  val id: String,
  val type: String,
  val position: Position,
  val data: WorkflowNodeData,
  val width: Int,
  val height: Int,
  val selected: Boolean
)

data class WorkflowModel(
  val id: String,
  val name: String,
  val nodes: List<WorkflowNode> = emptyList()
)

data class CreateWorkflowScheduleApiRequest(
  val name: String,
  val cronExpression: String,
  val timeZone: String? = null,
  val continuumWorkflowModel: WorkflowModel
)

data class WorkflowScheduleApiResponse(
  val scheduleId: UUID,
  val name: String,
  val ownedBy: String,
  val cronExpression: String,
  val timeZone: String?,
  val paused: Boolean,
  val nextRunTimes: List<Instant>,
  val createdAt: Instant,
  val updatedAt: Instant,
  val continuumWorkflowModel: WorkflowModel
)

@Component
class WorkflowScheduleApiClient(
  private val apiServerRestTemplate: RestTemplate,
  @Value("\${continuum.core.knime-scheduler.api-server-base-url}") private val baseUrl: String
) {

  private fun headers(ownedBy: String): HttpHeaders = HttpHeaders().apply { set(USER_ID_HEADER, ownedBy) }

  fun createSchedule(request: CreateWorkflowScheduleApiRequest, ownedBy: String): WorkflowScheduleApiResponse {
    try {
      val response = apiServerRestTemplate.exchange(
        "$baseUrl/api/v1/workflow/schedule",
        HttpMethod.POST,
        HttpEntity(request, headers(ownedBy)),
        WorkflowScheduleApiResponse::class.java
      )
      return response.body ?: throw KnimeWorkflowScheduleRequestInvalidException("Empty response creating schedule")
    } catch (ex: HttpClientErrorException.BadRequest) {
      throw KnimeWorkflowScheduleRequestInvalidException("Invalid schedule request: ${ex.message}")
    }
  }

  fun listSchedules(ownedBy: String): List<WorkflowScheduleApiResponse> {
    val response = apiServerRestTemplate.exchange(
      "$baseUrl/api/v1/workflow/schedule",
      HttpMethod.GET,
      HttpEntity<Void>(headers(ownedBy)),
      Array<WorkflowScheduleApiResponse>::class.java
    )
    return response.body?.toList() ?: emptyList()
  }

  fun getSchedule(ownedBy: String, scheduleId: UUID): WorkflowScheduleApiResponse? {
    return try {
      val response = apiServerRestTemplate.exchange(
        "$baseUrl/api/v1/workflow/schedule/$scheduleId",
        HttpMethod.GET,
        HttpEntity<Void>(headers(ownedBy)),
        WorkflowScheduleApiResponse::class.java
      )
      response.body
    } catch (ex: HttpClientErrorException.NotFound) {
      null
    }
  }

  fun pauseSchedule(ownedBy: String, scheduleId: UUID, note: String?) {
    exchangeVoidOrThrow(noteUrl("$baseUrl/api/v1/workflow/schedule/$scheduleId/pause", note), ownedBy)
  }

  fun unpauseSchedule(ownedBy: String, scheduleId: UUID, note: String?) {
    exchangeVoidOrThrow(noteUrl("$baseUrl/api/v1/workflow/schedule/$scheduleId/unpause", note), ownedBy)
  }

  private fun noteUrl(url: String, note: String?): String =
    UriComponentsBuilder.fromUriString(url)
      .apply { note?.let { queryParam("note", it) } }
      .toUriString()

  fun triggerNow(ownedBy: String, scheduleId: UUID) {
    exchangeVoidOrThrow("$baseUrl/api/v1/workflow/schedule/$scheduleId/trigger", ownedBy)
  }

  fun deleteSchedule(ownedBy: String, scheduleId: UUID) {
    try {
      apiServerRestTemplate.exchange(
        "$baseUrl/api/v1/workflow/schedule/$scheduleId",
        HttpMethod.DELETE,
        HttpEntity<Void>(headers(ownedBy)),
        Void::class.java
      )
    } catch (ex: HttpClientErrorException.NotFound) {
      throw KnimeWorkflowScheduleNotFoundException("Schedule $scheduleId not found for owner $ownedBy")
    }
  }

  private fun exchangeVoidOrThrow(url: String, ownedBy: String) {
    try {
      apiServerRestTemplate.exchange(url, HttpMethod.POST, HttpEntity<Void>(headers(ownedBy)), Void::class.java)
    } catch (ex: HttpClientErrorException.NotFound) {
      throw KnimeWorkflowScheduleNotFoundException("Schedule not found for owner $ownedBy")
    }
  }
}
