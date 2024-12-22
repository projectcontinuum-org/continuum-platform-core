package com.continuum.core.worker.activity

import com.continuum.core.commons.activity.IContinuumNodeActivity
import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.node.TriggerNodeModel
import io.temporal.spring.boot.ActivityImpl
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

@Component
@ActivityImpl(taskQueues = [TaskQueues.ACTIVITY_TASK_QUEUE])
class ContinuumNodeActivity(
    private val processNodesModelProvider: ObjectProvider<ProcessNodeModel>,
    private val triggerNodeModelProvider: ObjectProvider<TriggerNodeModel>
) : IContinuumNodeActivity {

    private val processNodeMap = mutableMapOf<String, ProcessNodeModel>()
    private val triggerNodeMap = mutableMapOf<String, TriggerNodeModel>()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ContinuumNodeActivity::class.java)
    }

    @PostConstruct
    fun onInit() {
        processNodesModelProvider.forEach {
            processNodeMap[it.javaClass.simpleName] = it
        }
        triggerNodeModelProvider.forEach {
            triggerNodeMap[it.javaClass.simpleName] = it
        }
        LOGGER.info("Registered process nodes: ${processNodeMap.keys}")
        LOGGER.info("Registered trigger nodes: ${triggerNodeMap.keys}")
    }

    override fun run(
        node: ContinuumWorkflowModel.Node,
        inputs: Map<String, PortData>
    ): Map<String, PortData> {
        // Find the node to execute
        if (processNodeMap.containsKey(node.data.nodeModel)) {
            return processNodeMap[node.data.nodeModel]!!.run(inputs)
        } else if (triggerNodeMap.containsKey(node.data.nodeModel)) {
            return triggerNodeMap[node.data.nodeModel]!!.run()
        }
        throw IllegalArgumentException("Node model not found: ${node.data.nodeModel}")
    }
}