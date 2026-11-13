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
import kotlin.test.assertSame

class NodeOutputServiceTest {

  private lateinit var nodeOutputRepository: NodeOutputRepository
  private lateinit var workflowRunRepository: WorkflowRunRepository
  private lateinit var service: NodeOutputService

  @BeforeEach
  fun setUp() {
    nodeOutputRepository = mock()
    workflowRunRepository = mock()
    service = NodeOutputService(nodeOutputRepository, workflowRunRepository)
  }

  @Test
  fun `owner can read their own node output`() {
    val page = Page<List<CellDto>>(
      data = emptyList(), currentPage = 0, currentPageSize = 10,
      totalPages = 0, totalElements = 0, hasNext = false, hasPrevious = false
    )
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("run-1", "alice")).thenReturn(true)
    whenever(nodeOutputRepository.getOutput("run-1", "node-1", "output", 0, 10)).thenReturn(page)

    assertSame(page, service.getOutput("alice", "run-1", "node-1", "output", 0, 10))
  }

  @Test
  fun `another owner's output is not found and no data is located or read`() {
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("run-1", "mallory")).thenReturn(false)

    assertThrows<ResourceNotFoundException> { service.getOutput("mallory", "run-1", "node-1", "output", 0, 10) }

    verify(nodeOutputRepository, never()).getOutput(any(), any(), any(), any(), any())
  }

  @Test
  fun `unknown workflow and another owner's workflow are indistinguishable`() {
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy(any(), any())).thenReturn(false)

    val unknown = assertThrows<ResourceNotFoundException> { service.getOutput("alice", "no-such-run", "n", "o", 0, 10) }
    val unowned = assertThrows<ResourceNotFoundException> { service.getOutput("mallory", "run-1", "n", "o", 0, 10) }

    kotlin.test.assertEquals(unknown.message, unowned.message)
  }

  @Test
  fun `validation of ids still applies to the owner`() {
    whenever(workflowRunRepository.existsByWorkflowIdAndOwnedBy("run-1", "alice")).thenReturn(true)
    whenever(nodeOutputRepository.getOutput("run-1", "../x", "output", 0, 10))
      .thenThrow(InvalidRequestException("Invalid nodeId"))

    assertThrows<InvalidRequestException> { service.getOutput("alice", "run-1", "../x", "output", 0, 10) }
  }
}
