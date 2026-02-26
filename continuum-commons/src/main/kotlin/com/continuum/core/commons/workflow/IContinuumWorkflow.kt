package com.continuum.core.commons.workflow

import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.model.WorkflowSnapshot
import io.temporal.common.SearchAttributeKey
import io.temporal.workflow.QueryMethod
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

@WorkflowInterface
interface IContinuumWorkflow {
  companion object {
    val WORKFLOW_FILE_PATH: SearchAttributeKey<String> = SearchAttributeKey.forKeyword("Continuum:WorkflowFileName")
    val WORKFLOW_STATUS: SearchAttributeKey<Long> = SearchAttributeKey.forLong("Continuum:ExecutionStatus")
  }

  @WorkflowMethod
  fun start(
    continuumWorkflow: ContinuumWorkflowModel
  ): Map<String, Map<String, PortData>>

  @QueryMethod
  fun getWorkflowSnapshot(): WorkflowSnapshot
}


/*
temporal operator search-attribute create --name "Continuum:WorkflowFileName" --type "Keyword"
temporal operator search-attribute create --name "Continuum:ExecutionStatus" --type "Int"
 */