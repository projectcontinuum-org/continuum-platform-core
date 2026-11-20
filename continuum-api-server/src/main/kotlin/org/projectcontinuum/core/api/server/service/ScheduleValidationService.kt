package org.projectcontinuum.core.api.server.service

import org.projectcontinuum.core.api.server.model.ScheduleValidationReport
import org.projectcontinuum.core.api.server.model.ScheduleValidationResult
import org.projectcontinuum.core.api.server.repository.jpa.WorkflowScheduleRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * On-demand check of stored schedules against the current node registry.
 *
 * Node properties are validated when a schedule is created, but schedules stored before that existed (or whose
 * node schema has changed since) were never checked. When one fires, its invalid node fails on the worker
 * (non-retriable `$error` output). This finds them. It only reads: nothing is paused, changed or deleted, because
 * schedules belong to their owners. Pages through the table, so memory stays bounded however many schedules exist.
 */
@Service
class ScheduleValidationService(
  private val workflowScheduleRepository: WorkflowScheduleRepository,
  private val workflowPropertiesValidator: WorkflowPropertiesValidator
) {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(ScheduleValidationService::class.java)
    const val PAGE_SIZE = 200
  }

  fun validateAll(): ScheduleValidationReport {
    val invalid = mutableListOf<ScheduleValidationResult>()
    val unregisteredOnly = mutableListOf<ScheduleValidationResult>()
    val failedToCheck = mutableListOf<UUID>()
    var checked = 0

    var page = workflowScheduleRepository.findAll(PageRequest.of(0, PAGE_SIZE, Sort.by("scheduleId")))
    while (true) {
      for (schedule in page.content) {
        checked++
        val problems = try {
          workflowPropertiesValidator.findProblems(schedule.workflow)
        } catch (e: Exception) {
          LOGGER.warn("Could not validate schedule {}: {}", schedule.scheduleId, e.message)
          failedToCheck += schedule.scheduleId
          continue
        }
        if (problems.isEmpty()) continue
        val result = ScheduleValidationResult(
          scheduleId = schedule.scheduleId,
          name = schedule.name,
          ownedBy = schedule.ownedBy,
          problems = problems.map { ScheduleValidationResult.Problem(it.nodeId, it.nodeModel, it.kind.name, it.detail) }
        )
        if (problems.all { it.kind == WorkflowPropertiesValidator.NodeProblem.Kind.UNREGISTERED_NODE }) {
          unregisteredOnly += result
        } else {
          invalid += result
        }
      }
      if (!page.hasNext()) break
      page = workflowScheduleRepository.findAll(page.nextPageable())
    }

    LOGGER.info(
      "Schedule validation: {} checked, {} invalid, {} with only unregistered nodes, {} not checked",
      checked, invalid.size, unregisteredOnly.size, failedToCheck.size
    )
    return ScheduleValidationReport(checked, invalid, unregisteredOnly, failedToCheck)
  }
}
