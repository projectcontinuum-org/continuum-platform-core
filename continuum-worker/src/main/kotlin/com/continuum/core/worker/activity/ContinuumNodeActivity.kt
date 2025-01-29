package com.continuum.core.worker.activity

import com.continuum.core.commons.activity.IContinuumNodeActivity
import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.model.PortDataStatus
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.node.TriggerNodeModel
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import io.temporal.activity.Activity
import io.temporal.spring.boot.ActivityImpl
import io.temporal.workflow.Workflow
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

@Component
@ActivityImpl(taskQueues = [TaskQueues.ACTIVITY_TASK_QUEUE])
class ContinuumNodeActivity(
    private val processNodesModelProvider: ObjectProvider<ProcessNodeModel>,
    private val triggerNodeModelProvider: ObjectProvider<TriggerNodeModel>,
    private val s3AsyncClient: S3AsyncClient,
    @Value("\${continuum.core.worker.cache-bucket-name}")
    private val cacheBucketName: String,
    @Value("\${continuum.core.worker.cache-bucket-base-path}")
    private val cacheBucketBasePath: String,
    @Value("\${continuum.core.worker.cache-storage-path}")
    private val cacheStoragePath: Path
) : IContinuumNodeActivity {

    private val processNodeMap = mutableMapOf<String, ProcessNodeModel>()
    private val triggerNodeMap = mutableMapOf<String, TriggerNodeModel>()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ContinuumNodeActivity::class.java)
    }

    @PostConstruct
    fun onInit() {
        processNodesModelProvider.forEach {
            processNodeMap[it.javaClass.name] = it
        }
        triggerNodeModelProvider.forEach {
            triggerNodeMap[it.javaClass.name] = it
        }
        LOGGER.info("Registered process nodes: ${processNodeMap.keys}")
        LOGGER.info("Registered trigger nodes: ${triggerNodeMap.keys}")
    }

    override fun run(
        node: ContinuumWorkflowModel.Node,
        inputs: Map<String, PortData>
    ): IContinuumNodeActivity.NodeActivityOutput {
        Files.createDirectories(cacheStoragePath.resolve("${Activity.getExecutionContext().info.runId}/${node.id}"))
        // Find the node to execute
        if (processNodeMap.containsKey(node.data.nodeModel)) {
            val nodeInputs = prepareNodeInputs(node.id, inputs)
            processNodeMap[node.data.nodeModel]!!.run(
                node = node,
                inputs = nodeInputs,
                nodeOutputWriter = NodeOutputWriter(cacheStoragePath.resolve("${Activity.getExecutionContext().info.runId}/${node.id}"))
            )

            return IContinuumNodeActivity.NodeActivityOutput(
                nodeId = node.id,
                outputs = prepareNodeOutputs(node.id)
            )
        } else if (triggerNodeMap.containsKey(node.data.nodeModel)) {
            triggerNodeMap[node.data.nodeModel]!!.run(
                node,
                nodeOutputWriter = NodeOutputWriter(cacheStoragePath.resolve("${Activity.getExecutionContext().info.runId}/${node.id}"))
            )
            return IContinuumNodeActivity.NodeActivityOutput(
                nodeId = node.id,
                outputs = prepareNodeOutputs(node.id)
            )
        }
        throw IllegalArgumentException("Node model not found: ${node.data.nodeModel}")
    }

    fun prepareNodeInputs(
        nodeId: String,
        inputs: Map<String, PortData>
    ): Map<String, NodeInputReader> {
        val workflowRunId = Activity.getExecutionContext().info.runId
        return inputs.mapValues {
            val filePath = cacheStoragePath.resolve("$workflowRunId/$nodeId/input.${it.key}.parquet")
            Files.deleteIfExists(filePath)
            val destinationKey = "$cacheBucketBasePath/${it.value.data.toString().removePrefix("{remote}")}"
            s3AsyncClient.getObject(
                GetObjectRequest.builder()
                    .bucket(cacheBucketName)
                    .key(destinationKey)
                    .build(),
                filePath
            ).get()
            NodeInputReader(filePath)
        }
    }

    fun prepareNodeOutputs(
        nodeId: String
    ): Map<String, PortData> {
        val workflowRunId = Activity.getExecutionContext().info.runId
        val nodeOutputPath = cacheStoragePath.resolve("$workflowRunId/$nodeId")
        val lisOutputFiles = nodeOutputPath.listDirectoryEntries().filter { it.fileName.toString().startsWith("output.") && it.fileName.toString().endsWith(".parquet") }
        return lisOutputFiles.map {
            val portId = it.fileName.toString().substringAfter("output.").substringBefore(".parquet")
            val relativeFileKey = "$workflowRunId/$nodeId/output.$portId.parquet"
            val destinationKey = "$cacheBucketBasePath/$relativeFileKey"
            s3AsyncClient.putObject(
                PutObjectRequest.builder()
                    .bucket(cacheBucketName)
                    .key(destinationKey)
                    .build(),
                it
            ).get()
            val portData = PortData(
                data = "{remote}${relativeFileKey}",
                contentType = "application/parquet",
                status = PortDataStatus.SUCCESS
            )
            Pair(portId, portData)
        }.associate { it }
    }
}