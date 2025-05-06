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
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.system.measureTimeMillis

@Component
@ActivityImpl(taskQueues = [TaskQueues.ACTIVITY_TASK_QUEUE])
class ContinuumNodeActivity(
    private val processNodesModelProvider: ObjectProvider<ProcessNodeModel>,
    private val triggerNodeModelProvider: ObjectProvider<TriggerNodeModel>,
    private val s3TransferManager: S3TransferManager,
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
            LOGGER.info("Downloading input files for node ${node.id} (${node.data.nodeModel})")
            val nodeInputs = prepareNodeInputs(node.id, inputs)
            LOGGER.info("Input files downloaded for node ${node.id} (${node.data.nodeModel})")
            processNodeMap[node.data.nodeModel]!!.run(
                node = node,
                inputs = nodeInputs,
                nodeOutputWriter = NodeOutputWriter(cacheStoragePath.resolve("${Activity.getExecutionContext().info.runId}/${node.id}"))
            )
            LOGGER.info("Uploading output files for node ${node.id} (${node.data.nodeModel})")
            val nodeOutput = prepareNodeOutputs(node.id)
            LOGGER.info("Output files uploaded for node ${node.id} (${node.data.nodeModel})")
            return IContinuumNodeActivity.NodeActivityOutput(
                nodeId = node.id,
                outputs = nodeOutput
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

        return IContinuumNodeActivity.NodeActivityOutput(
            nodeId = node.id,
            outputs = mapOf(
                "\$error" to PortData(
                    data = "Node model '${node.data.nodeModel}' not found",
                    contentType = "text/plain",
                    status = PortDataStatus.FAILED
                )
            )
        )
    }

    fun prepareNodeInputs(
        nodeId: String,
        inputs: Map<String, PortData>
    ): Map<String, NodeInputReader> {
        val workflowRunId = Activity.getExecutionContext().info.runId
        return inputs.mapValues {
            val filePath = cacheStoragePath.resolve("$workflowRunId/$nodeId/input.${it.key}.parquet")
            if (!Files.exists(filePath)) {
                val destinationKey = "$cacheBucketBasePath/${it.value.data.toString().removePrefix("{remote}")}"
                val uploadTime = measureTimeMillis {
                    s3TransferManager.downloadFile(
                        DownloadFileRequest.builder()
                            .getObjectRequest(
                                GetObjectRequest.builder()
                                    .bucket(cacheBucketName)
                                    .key(destinationKey)
                                    .build()
                            )
                            .destination(filePath)
                            .addTransferListener(
                                LoggingTransferListener.create()
                            )
                            .build()
                    ).completionFuture().get()
                }
                LOGGER.info("Download '$filePath' time: $uploadTime ms")
            } else {
                LOGGER.info("File '$filePath' already exists, skipping download")
            }
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

            val uploadTime = measureTimeMillis {
                s3TransferManager.uploadFile(
                    UploadFileRequest.builder()
                        .putObjectRequest(
                            PutObjectRequest.builder()
                                .bucket(cacheBucketName)
                                .key(destinationKey)
                                .build()
                        )
                        .source(it)
                        .addTransferListener(
                            LoggingTransferListener.create()
                        )
                        .build()
                ).completionFuture().get()
            }
            LOGGER.info("Upload '$it' time: $uploadTime ms")
            val portData = PortData(
                data = "{remote}${relativeFileKey}",
                contentType = "application/parquet",
                status = PortDataStatus.SUCCESS
            )
            Pair(portId, portData)
        }.associate { it }
    }
}