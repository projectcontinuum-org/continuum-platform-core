package org.projectcontinuum.core.api.server.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowScheduleEntity
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowScheduleRepository
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleValidationServiceTest {

  private lateinit var repository: WorkflowScheduleRepository
  private lateinit var nodeExplorerService: NodeExplorerService
  private lateinit var service: ScheduleValidationService

  private val schema = mapOf(
    "type" to "object",
    "properties" to mapOf("width" to mapOf("type" to "integer")),
    "required" to listOf("width")
  )

  @BeforeEach
  fun setUp() {
    repository = mock()
    nodeExplorerService = mock()
    whenever(nodeExplorerService.getNodeMetadata("org.test.Typed")).thenReturn(
      ContinuumWorkflowModel.NodeData(description = "", title = "Typed", nodeModel = "org.test.Typed", propertiesSchema = schema)
    )
    service = ScheduleValidationService(repository, WorkflowPropertiesValidator(nodeExplorerService))
  }

  private fun node(id: String, properties: Map<String, Any>, nodeModel: String = "org.test.Typed") = ContinuumWorkflowModel.Node(
    id = id, type = "default", position = ContinuumWorkflowModel.Position(0.0, 0.0), width = 1, height = 1, selected = false,
    // A stored schedule carries whatever schema the client sent; it must not be what the check uses.
    data = ContinuumWorkflowModel.NodeData(description = "", title = "T", nodeModel = nodeModel, properties = properties, propertiesSchema = emptyMap())
  )

  private fun schedule(name: String, vararg nodes: ContinuumWorkflowModel.Node) = WorkflowScheduleEntity(
    scheduleId = UUID.randomUUID(), name = name, ownedBy = "alice", cronExpression = "0 * * * *",
    workflow = ContinuumWorkflowModel(id = "wf-$name", name = name, nodes = nodes.toList())
  )

  /** The repository returns [schedules] in pages of [ScheduleValidationService.PAGE_SIZE]. */
  private fun stored(vararg schedules: WorkflowScheduleEntity) {
    whenever(repository.findAll(any<Pageable>())).thenAnswer { invocation ->
      val pageable = invocation.getArgument<Pageable>(0)
      val from = pageable.offset.toInt().coerceAtMost(schedules.size)
      val to = (from + pageable.pageSize).coerceAtMost(schedules.size)
      PageImpl(schedules.toList().subList(from, to), pageable, schedules.size.toLong())
    }
  }

  @Test
  fun `reports schedules whose properties fail the registered schema`() {
    val good = schedule("good", node("a", mapOf("width" to 1)))
    val bad = schedule("bad", node("a", mapOf("width" to 1)), node("b", mapOf("width" to "wide")))
    stored(good, bad)

    val report = service.validateAll()

    assertEquals(2, report.checked)
    assertFalse(report.valid)
    val result = report.invalid.single()
    assertEquals(bad.scheduleId, result.scheduleId)
    assertEquals("alice", result.ownedBy)
    val problem = result.problems.single()
    assertEquals("b", problem.nodeId)
    assertEquals("INVALID_PROPERTIES", problem.kind)
    assertTrue(problem.detail.contains("/width"), problem.detail)
  }

  @Test
  fun `all valid schedules give a clean report`() {
    stored(schedule("a", node("a", mapOf("width" to 1))), schedule("b", node("b", mapOf("width" to 2))))

    val report = service.validateAll()

    assertTrue(report.valid)
    assertEquals(2, report.checked)
  }

  @Test
  fun `no stored schedules is a clean, empty report`() {
    stored()

    val report = service.validateAll()

    assertTrue(report.valid)
    assertEquals(0, report.checked)
  }

  @Test
  fun `a schedule whose only problem is an unregistered node is reported separately`() {
    val waiting = schedule("waiting", node("x", emptyMap(), nodeModel = "org.test.NotYetRegistered"))
    stored(waiting)

    val report = service.validateAll()

    assertTrue(report.invalid.isEmpty())
    assertEquals(listOf(waiting.scheduleId), report.unregisteredOnly.map { it.scheduleId })
    assertEquals("UNREGISTERED_NODE", report.unregisteredOnly.single().problems.single().kind)
  }

  @Test
  fun `a schedule with both an invalid and an unregistered node counts as invalid`() {
    val mixed = schedule("mixed", node("a", emptyMap()), node("x", emptyMap(), nodeModel = "org.test.NotYetRegistered"))
    stored(mixed)

    val report = service.validateAll()

    assertEquals(listOf(mixed.scheduleId), report.invalid.map { it.scheduleId })
    assertEquals(2, report.invalid.single().problems.size)
  }

  @Test
  fun `one schedule failing to check does not stop the others`() {
    val broken = schedule("broken", node("a", mapOf("width" to 1), nodeModel = "org.test.Explodes"))
    val bad = schedule("bad", node("b", mapOf("width" to "wide")))
    whenever(nodeExplorerService.getNodeMetadata("org.test.Explodes")).thenThrow(IllegalStateException("db down"))
    stored(broken, bad)

    val report = service.validateAll()

    assertEquals(listOf(broken.scheduleId), report.failedToCheck)
    assertEquals(listOf(bad.scheduleId), report.invalid.map { it.scheduleId })
    assertFalse(report.valid)
  }

  @Test
  fun `reads every page and checks every schedule`() {
    val count = ScheduleValidationService.PAGE_SIZE * 2 + 5
    val schedules = (1..count).map { i ->
      if (i % 100 == 0) schedule("bad-$i", node("n", mapOf("width" to "x"))) else schedule("ok-$i", node("n", mapOf("width" to i)))
    }
    stored(*schedules.toTypedArray())

    val report = service.validateAll()

    assertEquals(count, report.checked)
    assertEquals(4, report.invalid.size)
    val pages = argumentCaptor<Pageable>()
    verify(repository, times(3)).findAll(pages.capture())
    assertEquals(listOf(0, 1, 2), pages.allValues.map { it.pageNumber })
  }

  @Test
  fun `it only reads - nothing is saved or deleted`() {
    stored(schedule("bad", node("b", mapOf("width" to "wide"))))

    service.validateAll()

    verify(repository).findAll(any<Pageable>())
    verifyNoMoreInteractions(repository)
  }

  @Test
  fun `each node type is looked up once however many schedules use it`() {
    stored(*(1..10).map { schedule("s$it", node("n", mapOf("width" to it))) }.toTypedArray())

    service.validateAll()

    // Once per schedule at most (the registry read is cached at the repository in the running app).
    verify(nodeExplorerService, times(10)).getNodeMetadata("org.test.Typed")
  }

  @Test
  fun `pages are requested in a stable order`() {
    stored(schedule("a", node("a", mapOf("width" to 1))))

    service.validateAll()

    val page = argumentCaptor<Pageable>()
    verify(repository).findAll(page.capture())
    assertEquals(PageRequest.of(0, ScheduleValidationService.PAGE_SIZE).pageSize, page.firstValue.pageSize)
    assertTrue(page.firstValue.sort.getOrderFor("scheduleId") != null, "sorted by id so paging cannot skip or repeat rows")
  }
}
