package org.projectcontinuum.core.knime.scheduler.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.knime.scheduler.client.CreateWorkflowScheduleApiRequest
import org.projectcontinuum.core.knime.scheduler.client.WorkflowScheduleApiClient
import org.projectcontinuum.core.knime.scheduler.client.WorkflowScheduleApiResponse
import org.projectcontinuum.core.knime.scheduler.entity.KnimeWorkflowEntity
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowNotFoundException
import org.projectcontinuum.core.knime.scheduler.exception.KnimeWorkflowScheduleNotFoundException
import org.projectcontinuum.core.knime.scheduler.model.CreateKnimeWorkflowScheduleRequest
import org.projectcontinuum.core.knime.scheduler.repository.KnimeWorkflowRepository
import org.projectcontinuum.core.knime.scheduler.util.KnimeScheduleWorkflowMapper
import java.time.Instant
import java.util.UUID

class KnimeWorkflowScheduleServiceTest {

  private val workflowScheduleApiClient: WorkflowScheduleApiClient = mock()
  private val knimeWorkflowRepository: KnimeWorkflowRepository = mock()
  private val ownedBy = "alice"
  private val publicBaseUrl = "http://localhost:8085"

  private val service = KnimeWorkflowScheduleService(workflowScheduleApiClient, knimeWorkflowRepository, publicBaseUrl)

  private fun knimeApiResponse(scheduleId: UUID, knimeWorkflowId: UUID, paused: Boolean = false): WorkflowScheduleApiResponse {
    val model = KnimeScheduleWorkflowMapper.toWorkflowModel(
      name = "Nightly run",
      knimeWorkflowId = knimeWorkflowId,
      contentUrl = "$publicBaseUrl/api/v1/knime-workflows/$knimeWorkflowId/content",
      resetWorkflow = false,
      timeoutSeconds = 300
    )
    return WorkflowScheduleApiResponse(
      scheduleId = scheduleId,
      name = "Nightly run",
      ownedBy = ownedBy,
      cronExpression = "0 0 * * *",
      timeZone = null,
      paused = paused,
      nextRunTimes = emptyList(),
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      continuumWorkflowModel = model
    )
  }

  private fun entity(knimeWorkflowId: UUID) = KnimeWorkflowEntity(
    workflowId = knimeWorkflowId,
    ownedBy = ownedBy,
    fileName = "workflow.knwf",
    objectKey = "knime-workflows/users/$ownedBy/$knimeWorkflowId.knwf",
    bucketName = "continuum-knime-workflows",
    sizeBytes = 42L,
    contentType = "application/octet-stream"
  )

  @Test
  fun `createSchedule throws when the referenced knwf is not owned by the caller and never calls the API`() {
    val knimeWorkflowId = UUID.randomUUID()
    whenever(knimeWorkflowRepository.findByWorkflowIdAndOwnedBy(knimeWorkflowId, ownedBy)).thenReturn(null)
    val request = CreateKnimeWorkflowScheduleRequest(
      name = "Nightly run", cronExpression = "0 0 * * *", knimeWorkflowId = knimeWorkflowId
    )

    assertThrows(KnimeWorkflowNotFoundException::class.java) { service.createSchedule(request, ownedBy) }

    verify(workflowScheduleApiClient, never()).createSchedule(any(), any())
  }

  @Test
  fun `createSchedule builds a content URL pointing back at this module and delegates to the API client`() {
    val knimeWorkflowId = UUID.randomUUID()
    val scheduleId = UUID.randomUUID()
    whenever(knimeWorkflowRepository.findByWorkflowIdAndOwnedBy(knimeWorkflowId, ownedBy)).thenReturn(entity(knimeWorkflowId))
    whenever(workflowScheduleApiClient.createSchedule(any(), eq(ownedBy))).thenReturn(knimeApiResponse(scheduleId, knimeWorkflowId))
    val request = CreateKnimeWorkflowScheduleRequest(
      name = "Nightly run", cronExpression = "0 0 * * *", knimeWorkflowId = knimeWorkflowId
    )

    val response = service.createSchedule(request, ownedBy)

    verify(workflowScheduleApiClient).createSchedule(
      org.mockito.kotlin.argThat<CreateWorkflowScheduleApiRequest> { req ->
        req.name == "Nightly run" &&
          req.cronExpression == "0 0 * * *" &&
          KnimeScheduleWorkflowMapper.extractKnimeWorkflowId(req.continuumWorkflowModel) == knimeWorkflowId
      },
      eq(ownedBy)
    )
    assertEquals(scheduleId, response.scheduleId)
    assertEquals(knimeWorkflowId, response.knimeWorkflowId)
  }

  @Test
  fun `getSchedule throws KnimeWorkflowScheduleNotFoundException when the API client returns null`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleApiClient.getSchedule(ownedBy, scheduleId)).thenReturn(null)

    assertThrows(KnimeWorkflowScheduleNotFoundException::class.java) { service.getSchedule(ownedBy, scheduleId) }
  }

  @Test
  fun `getSchedule returns the mapped response for a KNIME-shaped schedule`() {
    val scheduleId = UUID.randomUUID()
    val knimeWorkflowId = UUID.randomUUID()
    whenever(workflowScheduleApiClient.getSchedule(ownedBy, scheduleId)).thenReturn(knimeApiResponse(scheduleId, knimeWorkflowId))

    val response = service.getSchedule(ownedBy, scheduleId)

    assertEquals(knimeWorkflowId, response.knimeWorkflowId)
  }

  @Test
  fun `listSchedules filters out non-KNIME-shaped schedules`() {
    val knimeWorkflowId = UUID.randomUUID()
    val nonKnimeResponse = knimeApiResponse(UUID.randomUUID(), knimeWorkflowId).copy(
      continuumWorkflowModel = org.projectcontinuum.core.commons.model.ContinuumWorkflowModel(
        id = "wf-2", name = "Generic workflow", nodes = emptyList()
      )
    )
    val knimeResponse = knimeApiResponse(UUID.randomUUID(), knimeWorkflowId)
    whenever(workflowScheduleApiClient.listSchedules(ownedBy)).thenReturn(listOf(nonKnimeResponse, knimeResponse))

    val result = service.listSchedules(ownedBy)

    assertEquals(1, result.size)
    assertEquals(knimeResponse.scheduleId, result[0].scheduleId)
  }

  @Test
  fun `pauseSchedule throws when the schedule is not found and never delegates`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleApiClient.getSchedule(ownedBy, scheduleId)).thenReturn(null)

    assertThrows(KnimeWorkflowScheduleNotFoundException::class.java) { service.pauseSchedule(ownedBy, scheduleId, null) }

    verify(workflowScheduleApiClient, never()).pauseSchedule(any(), any(), any())
  }

  @Test
  fun `pauseSchedule delegates to the API client when the schedule exists`() {
    val scheduleId = UUID.randomUUID()
    val knimeWorkflowId = UUID.randomUUID()
    whenever(workflowScheduleApiClient.getSchedule(ownedBy, scheduleId)).thenReturn(knimeApiResponse(scheduleId, knimeWorkflowId))

    service.pauseSchedule(ownedBy, scheduleId, "maintenance")

    verify(workflowScheduleApiClient).pauseSchedule(ownedBy, scheduleId, "maintenance")
  }

  @Test
  fun `deleteSchedule delegates to the API client when the schedule exists`() {
    val scheduleId = UUID.randomUUID()
    val knimeWorkflowId = UUID.randomUUID()
    whenever(workflowScheduleApiClient.getSchedule(ownedBy, scheduleId)).thenReturn(knimeApiResponse(scheduleId, knimeWorkflowId))

    service.deleteSchedule(ownedBy, scheduleId)

    verify(workflowScheduleApiClient).deleteSchedule(ownedBy, scheduleId)
  }
}
