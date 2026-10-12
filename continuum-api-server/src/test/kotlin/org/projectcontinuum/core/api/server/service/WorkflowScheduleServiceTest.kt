package org.projectcontinuum.core.api.server.service

import io.temporal.client.schedules.Schedule
import io.temporal.client.schedules.ScheduleActionStartWorkflow
import io.temporal.client.schedules.ScheduleClient
import io.temporal.client.schedules.ScheduleDescription
import io.temporal.client.schedules.ScheduleException
import io.temporal.client.schedules.ScheduleHandle
import io.temporal.client.schedules.ScheduleInfo
import io.temporal.client.schedules.ScheduleState
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowScheduleEntity
import org.projectcontinuum.core.api.server.exception.WorkflowScheduleNotFoundException
import org.projectcontinuum.core.api.server.model.CreateWorkflowScheduleRequest
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowScheduleRepository
import org.projectcontinuum.core.commons.context.ContinuumOwnerContext
import org.projectcontinuum.core.commons.context.ContinuumScheduleContext
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowScheduleServiceTest {

  private lateinit var scheduleClient: ScheduleClient
  private lateinit var workflowScheduleRepository: WorkflowScheduleRepository
  private lateinit var service: WorkflowScheduleService

  private val ownedBy = "alice"

  @BeforeEach
  fun setUp() {
    scheduleClient = mock()
    workflowScheduleRepository = mock()
    service = WorkflowScheduleService(scheduleClient, workflowScheduleRepository)
  }

  private fun workflowModel(): ContinuumWorkflowModel =
    ContinuumWorkflowModel(id = "wf-1", name = "My Workflow", nodes = emptyList(), edges = emptyList())

  private fun request(cron: String = "0 * * * *"): CreateWorkflowScheduleRequest =
    CreateWorkflowScheduleRequest(name = "Hourly run", cronExpression = cron, continuumWorkflowModel = workflowModel())

  private fun handleReturning(scheduleId: UUID, paused: Boolean = false, nextRunTimes: List<Instant> = emptyList()): ScheduleHandle {
    val handle: ScheduleHandle = mock()
    val description = ScheduleDescription(
      scheduleId.toString(),
      ScheduleInfo(0, 0, 0, emptyList(), emptyList(), nextRunTimes, Instant.now(), Instant.now()),
      Schedule.newBuilder()
        .setAction(ScheduleActionStartWorkflow.newBuilder().setWorkflowType("IContinuumWorkflow").build())
        .setSpec(io.temporal.client.schedules.ScheduleSpec.newBuilder().setCronExpressions(listOf("0 * * * *")).build())
        .setState(ScheduleState.newBuilder().setPaused(paused).build())
        .build(),
      emptyMap(),
      null,
      emptyMap(),
      null
    )
    whenever(handle.describe()).thenReturn(description)
    return handle
  }

  @Test
  fun `createSchedule builds a Schedule with correct action, spec and owner+schedule headers, then saves entity`() {
    val req = request(cron = "0 * * * *")
    val handle = handleReturning(UUID.randomUUID())
    whenever(scheduleClient.getHandle(any())).thenReturn(handle)
    whenever(workflowScheduleRepository.save(any())).thenAnswer { it.arguments[0] }

    val response = service.createSchedule(req, ownedBy)

    verify(scheduleClient).createSchedule(
      any(),
      argThat { schedule ->
        val action = schedule.action as ScheduleActionStartWorkflow
        val ownerPayload = action.header.values[ContinuumOwnerContext.HEADER_KEY]
        val schedulePayload = action.header.values[ContinuumScheduleContext.HEADER_KEY]
        action.workflowType == "IContinuumWorkflow" &&
          action.options.taskQueue == org.projectcontinuum.core.commons.constant.TaskQueues.WORKFLOW_TASK_QUEUE &&
          schedule.spec.cronExpressions == listOf("0 * * * *") &&
          ownerPayload != null && ownerPayload.data.toStringUtf8() == ownedBy &&
          schedulePayload != null && schedulePayload.data.toStringUtf8() == action.options.workflowId
      },
      any()
    )
    verify(workflowScheduleRepository).save(
      argThat<WorkflowScheduleEntity> { entity ->
        entity.name == "Hourly run" && entity.ownedBy == ownedBy && entity.cronExpression == "0 * * * *" && entity.workflow == req.continuumWorkflowModel
      }
    )
    assertEquals(ownedBy, response.ownedBy)
    assertEquals("0 * * * *", response.cronExpression)
  }

  @Test
  fun `createSchedule with an invalid node throws before any Temporal or DB interaction`() {
    val badNode = ContinuumWorkflowModel.Node(
      id = "n1",
      type = "process",
      position = ContinuumWorkflowModel.Position(0.0, 0.0),
      width = 100,
      height = 100,
      selected = false,
      data = ContinuumWorkflowModel.NodeData(
        title = "Bad",
        description = "",
        nodeModel = "org.test.Bad",
        properties = mapOf("foo" to "not-a-number"),
        propertiesSchema = mapOf(
          "type" to "object",
          "properties" to mapOf("foo" to mapOf("type" to "integer")),
          "required" to listOf("foo")
        )
      )
    )
    val model = ContinuumWorkflowModel(id = "wf-1", name = "Bad Workflow", nodes = listOf(badNode), edges = emptyList())
    val req = CreateWorkflowScheduleRequest(name = "Bad", cronExpression = "0 * * * *", continuumWorkflowModel = model)

    assertThrows<AssertionError> { service.createSchedule(req, ownedBy) }

    verify(scheduleClient, never()).createSchedule(any(), any(), any())
    verify(workflowScheduleRepository, never()).save(any())
  }

  @Test
  fun `createSchedule propagates ScheduleException and never saves to DB`() {
    whenever(scheduleClient.createSchedule(any(), any(), any())).thenThrow(ScheduleException(RuntimeException("boom")))

    assertThrows<ScheduleException> { service.createSchedule(request(), ownedBy) }

    verify(workflowScheduleRepository, never()).save(any())
  }

  @Test
  fun `listSchedules joins DB rows with live ScheduleHandle describe results`() {
    val scheduleId = UUID.randomUUID()
    val entity = WorkflowScheduleEntity(
      scheduleId = scheduleId,
      name = "Nightly",
      ownedBy = ownedBy,
      cronExpression = "0 0 * * *",
      workflow = workflowModel()
    )
    whenever(workflowScheduleRepository.findByOwnedBy(ownedBy)).thenReturn(listOf(entity))
    val nextRun = Instant.now().plusSeconds(3600)
    val handle = handleReturning(scheduleId, paused = true, nextRunTimes = listOf(nextRun))
    whenever(scheduleClient.getHandle(scheduleId.toString())).thenReturn(handle)

    val result = service.listSchedules(ownedBy)

    assertEquals(1, result.size)
    assertTrue(result[0].paused)
    assertEquals(listOf(nextRun), result[0].nextRunTimes)
  }

  @Test
  fun `listSchedules filters by name when provided`() {
    val scheduleId = UUID.randomUUID()
    val entity = WorkflowScheduleEntity(
      scheduleId = scheduleId,
      name = "My Workflow",
      ownedBy = ownedBy,
      cronExpression = "0 0 * * *",
      workflow = workflowModel()
    )
    whenever(workflowScheduleRepository.findByOwnedByAndName(ownedBy, "My Workflow")).thenReturn(listOf(entity))
    val handle = handleReturning(scheduleId)
    whenever(scheduleClient.getHandle(scheduleId.toString())).thenReturn(handle)

    val result = service.listSchedules(ownedBy, "My Workflow")

    assertEquals(1, result.size)
    assertEquals("My Workflow", result[0].name)
    verify(workflowScheduleRepository, never()).findByOwnedBy(any())
  }

  @Test
  fun `getSchedule returns null when schedule not owned by caller`() {
    val scheduleId = UUID.randomUUID()
    val entity = WorkflowScheduleEntity(
      scheduleId = scheduleId,
      name = "Nightly",
      ownedBy = "bob",
      cronExpression = "0 0 * * *",
      workflow = workflowModel()
    )
    whenever(workflowScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(entity))

    val result = service.getSchedule(ownedBy, scheduleId)

    assertNull(result)
  }

  @Test
  fun `pauseSchedule delegates to ScheduleHandle pause with note`() {
    val scheduleId = UUID.randomUUID()
    val entity = WorkflowScheduleEntity(scheduleId, "N", ownedBy, "0 0 * * *", workflow = workflowModel())
    whenever(workflowScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(entity))
    val handle: ScheduleHandle = mock()
    whenever(scheduleClient.getHandle(scheduleId.toString())).thenReturn(handle)

    service.pauseSchedule(ownedBy, scheduleId, "maintenance")

    verify(handle).pause("maintenance")
  }

  @Test
  fun `pauseSchedule throws WorkflowScheduleNotFoundException for cross-tenant access and never calls Temporal`() {
    val scheduleId = UUID.randomUUID()
    val entity = WorkflowScheduleEntity(scheduleId, "N", "bob", "0 0 * * *", workflow = workflowModel())
    whenever(workflowScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(entity))

    assertThrows<WorkflowScheduleNotFoundException> { service.pauseSchedule(ownedBy, scheduleId, null) }

    verify(scheduleClient, never()).getHandle(any())
  }

  @Test
  fun `unpauseSchedule delegates to ScheduleHandle unpause`() {
    val scheduleId = UUID.randomUUID()
    val entity = WorkflowScheduleEntity(scheduleId, "N", ownedBy, "0 0 * * *", workflow = workflowModel())
    whenever(workflowScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(entity))
    val handle: ScheduleHandle = mock()
    whenever(scheduleClient.getHandle(scheduleId.toString())).thenReturn(handle)

    service.unpauseSchedule(ownedBy, scheduleId, null)

    verify(handle).unpause()
  }

  @Test
  fun `triggerNow delegates to ScheduleHandle trigger`() {
    val scheduleId = UUID.randomUUID()
    val entity = WorkflowScheduleEntity(scheduleId, "N", ownedBy, "0 0 * * *", workflow = workflowModel())
    whenever(workflowScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(entity))
    val handle: ScheduleHandle = mock()
    whenever(scheduleClient.getHandle(scheduleId.toString())).thenReturn(handle)

    service.triggerNow(ownedBy, scheduleId)

    verify(handle).trigger()
  }

  @Test
  fun `deleteSchedule deletes Temporal schedule then DB row`() {
    val scheduleId = UUID.randomUUID()
    val entity = WorkflowScheduleEntity(scheduleId, "N", ownedBy, "0 0 * * *", workflow = workflowModel())
    whenever(workflowScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(entity))
    val handle: ScheduleHandle = mock()
    whenever(scheduleClient.getHandle(scheduleId.toString())).thenReturn(handle)

    service.deleteSchedule(ownedBy, scheduleId)

    verify(handle).delete()
    verify(workflowScheduleRepository).deleteById(scheduleId)
  }

  @Test
  fun `deleteSchedule throws for non-existent schedule and never touches Temporal or DB`() {
    val scheduleId = UUID.randomUUID()
    whenever(workflowScheduleRepository.findById(scheduleId)).thenReturn(Optional.empty())

    assertThrows<WorkflowScheduleNotFoundException> { service.deleteSchedule(ownedBy, scheduleId) }

    verify(scheduleClient, never()).getHandle(any())
    verify(workflowScheduleRepository, never()).deleteById(any())
  }
}
