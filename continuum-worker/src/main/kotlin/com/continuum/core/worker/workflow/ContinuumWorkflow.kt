package com.continuum.core.worker.workflow

import io.temporal.common.SearchAttributeKey
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

@WorkflowInterface
interface ContinuumWorkflow {
  companion object {
    val STATUS_ATTR: SearchAttributeKey<String> = SearchAttributeKey.forKeyword("CustomStringField")
  }

  @WorkflowMethod
  fun start()
}