package com.continuum.core.commons.workflow

import com.continuum.core.commons.model.ContinuumWorkflowModel
import io.temporal.common.SearchAttributeKey
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

@WorkflowInterface
interface IContinuumWorkflow {
  companion object {
    val STATUS_ATTR: SearchAttributeKey<String> = SearchAttributeKey.forKeyword("CustomStringField")
  }

  @WorkflowMethod
  fun start(
    continuumWorkflow: ContinuumWorkflowModel
  )
}