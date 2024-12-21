package com.continuum.core.worker.activity

import com.continuum.core.commons.activity.IContinuumNodeActivity
import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.model.ContinuumWorkflowModel
import io.temporal.spring.boot.ActivityImpl
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@ActivityImpl(taskQueues = [TaskQueues.ACTIVITY_TASK_QUEUE])
class ContinuumNodeActivity: IContinuumNodeActivity {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(ContinuumNodeActivity::class.java)
  }

  override fun run(
    node: ContinuumWorkflowModel.Node
  ): Map<String, Any> {
    LOGGER.info("Executing node: '${node.data.nodeModel}' id=${node.id}")
    return emptyMap()
  }
}