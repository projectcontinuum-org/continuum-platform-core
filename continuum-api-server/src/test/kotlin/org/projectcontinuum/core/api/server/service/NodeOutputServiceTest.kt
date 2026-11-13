package org.projectcontinuum.core.api.server.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import org.projectcontinuum.core.api.server.exception.ResourceNotFoundException
import org.projectcontinuum.core.api.server.model.CellDto
import org.projectcontinuum.core.api.server.model.Page
import org.projectcontinuum.core.api.server.repository.NodeOutputRepository
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Node outputs are stored under the Temporal RUN id, which differs from the workflow id the API returns. These tests
 * use two distinct ids on purpose: ownership must be looked up by run id, never by workflow id.
 */
class NodeOutputServiceTest {

  private val workflowId = "wf-7cac18ee"
  private val runId = "run-05063841"

  private lateinit var nodeOutputRepository: NodeOutputRepository
  private lateinit var workflowRunRepository: WorkflowRunRepository
  private lateinit var service: NodeOutputService

  @BeforeEach
  fun setUp() {
    nodeOutputRepository = mock()
    workflowRunRepository = mock()
    service = NodeOutputService(nodeOutputRepository, workflowRunRepository)
  }

  private fun emptyPage() = Page<List<CellDto>>(
    data = emptyList(), currentPage = 0, currentPageSize = 10,
    totalPages = 0, totalElements = 0, hasNext = false, hasPrevious = false
  )

  @Test
  fun `owner can read the output of their own run, addressed by run id`() {
    val page = emptyPage()
    whenever(workflowRunRepository.existsByRunIdAndOwnedBy(runId, "alice")).thenReturn(true)
    whenever(nodeOutputRepository.getOutput(runId, "node-1", "output", 0, 10)).thenReturn(page)

    assertSame(page, service.getOutput("alice", runId, "node-1", "output", 0, 10))
  }

  @Test
  fun `the workflow id is not accepted as a run id`() {
    // Alice owns workflow wf-7cac18ee, whose run id is run-05063841. Only the run id is a valid output address.
    whenever(workflowRunRepository.existsByRunIdAndOwnedBy(runId, "alice")).thenReturn(true)
    whenever(workflowRunRepository.existsByRunIdAndOwnedBy(workflowId, "alice")).thenReturn(false)

    assertThrows<ResourceNotFoundException> { service.getOutput("alice", workflowId, "node-1", "output", 0, 10) }
    verify(nodeOutputRepository, never()).getOutput(any(), any(), any(), any(), any())
  }

  @Test
  fun `ownership is checked by run id and never by workflow id`() {
    whenever(workflowRunRepository.existsByRunIdAndOwnedBy(runId, "alice")).thenReturn(true)
    whenever(nodeOutputRepository.getOutput(any(), any(), any(), any(), any())).thenReturn(emptyPage())

    service.getOutput("alice", runId, "node-1", "output", 0, 10)

    verify(workflowRunRepository).existsByRunIdAndOwnedBy(runId, "alice")
    verify(workflowRunRepository, never()).existsByWorkflowIdAndOwnedBy(any(), any())
  }

  @Test
  fun `another owner's output is not found and no data is located or read`() {
    whenever(workflowRunRepository.existsByRunIdAndOwnedBy(runId, "mallory")).thenReturn(false)

    assertThrows<ResourceNotFoundException> { service.getOutput("mallory", runId, "node-1", "output", 0, 10) }

    verify(nodeOutputRepository, never()).getOutput(any(), any(), any(), any(), any())
  }

  @Test
  fun `unknown run and another owner's run are indistinguishable`() {
    whenever(workflowRunRepository.existsByRunIdAndOwnedBy(any(), any())).thenReturn(false)

    val unknown = assertThrows<ResourceNotFoundException> { service.getOutput("alice", "no-such-run", "n", "o", 0, 10) }
    val unowned = assertThrows<ResourceNotFoundException> { service.getOutput("mallory", runId, "n", "o", 0, 10) }

    assertEquals(unknown.message, unowned.message)
  }

  @Test
  fun `a run whose run id has not been recorded yet is not readable`() {
    // run_id is written by the message-bridge from the first workflow event; until then nothing matches.
    whenever(workflowRunRepository.existsByRunIdAndOwnedBy(any(), any())).thenReturn(false)

    assertThrows<ResourceNotFoundException> { service.getOutput("alice", runId, "node-1", "output", 0, 10) }
  }

  @Test
  fun `validation of ids still applies to the owner`() {
    whenever(workflowRunRepository.existsByRunIdAndOwnedBy(runId, "alice")).thenReturn(true)
    whenever(nodeOutputRepository.getOutput(runId, "../x", "output", 0, 10))
      .thenThrow(InvalidRequestException("Invalid nodeId"))

    assertThrows<InvalidRequestException> { service.getOutput("alice", runId, "../x", "output", 0, 10) }
  }
}
