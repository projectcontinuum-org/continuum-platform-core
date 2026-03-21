package org.projectcontinuum.core.api.server.service

import io.github.perplexhub.rsql.RSQLJPASupport
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunEntity
import org.projectcontinuum.core.api.server.entity.jpa.WorkflowRunSummaryEntity
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunRepository
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowRunSummaryRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WorkflowRunService(
  val workflowRunRepository: WorkflowRunRepository,
  val workflowRunSummaryRepository: WorkflowRunSummaryRepository
) {

  private fun <T> ownedBySpec(ownedBy: String): Specification<T> {
    return Specification { root, _, cb -> cb.equal(root.get<String>("ownedBy"), ownedBy) }
  }

  /**
   * List workflow runs WITHOUT the heavy `data` column.
   * Uses WorkflowRunSummaryEntity which maps to the same table but excludes JSONB data.
   */
  fun findAll(ownedBy: String, filter: String?, page: Int, size: Int, sort: String?): Page<WorkflowRunSummaryEntity> {
    val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))

    var spec: Specification<WorkflowRunSummaryEntity> = Specification.where(ownedBySpec(ownedBy))
    if (!filter.isNullOrBlank()) {
      spec = spec.and(RSQLJPASupport.toSpecification(filter))
    }
    if (!sort.isNullOrBlank()) {
      spec = spec.and(RSQLJPASupport.toSort(sort))
    }

    return workflowRunSummaryRepository.findAll(spec, pageable)
  }

  /**
   * Get a single workflow run WITH the full `data` column.
   * Used by the execution viewer to display workflow snapshot and outputs.
   */
  fun findById(ownedBy: String, workflowId: UUID): WorkflowRunEntity? {
    val spec: Specification<WorkflowRunEntity> = Specification.where(ownedBySpec<WorkflowRunEntity>(ownedBy))
      .and(Specification { root, _, cb -> cb.equal(root.get<UUID>("workflowId"), workflowId) })
    return workflowRunRepository.findOne(spec).orElse(null)
  }

  fun count(ownedBy: String, filter: String?): Long {
    var spec: Specification<WorkflowRunSummaryEntity> = Specification.where(ownedBySpec(ownedBy))
    if (!filter.isNullOrBlank()) {
      spec = spec.and(RSQLJPASupport.toSpecification(filter))
    }
    return workflowRunSummaryRepository.count(spec)
  }

  fun findDistinctWorkflows(ownedBy: String, filter: String?, page: Int, size: Int): Page<String> {
    val pageable = PageRequest.of(page, size)

    if (filter.isNullOrBlank()) {
      return workflowRunRepository.findDistinctWorkflowUris(ownedBy, pageable)
    }

    // When there's an RSQL filter (e.g. time range), use the lightweight summary entity
    var spec: Specification<WorkflowRunSummaryEntity> = Specification.where(ownedBySpec(ownedBy))
    spec = spec.and(RSQLJPASupport.toSpecification(filter))

    val allMatching = workflowRunSummaryRepository.findAll(spec)
    val distinctUris = allMatching.map { it.workflowUri.toString() }.distinct().sorted()
    val start = (page * size).coerceAtMost(distinctUris.size)
    val end = ((page + 1) * size).coerceAtMost(distinctUris.size)
    return PageImpl(distinctUris.subList(start, end), pageable, distinctUris.size.toLong())
  }
}
