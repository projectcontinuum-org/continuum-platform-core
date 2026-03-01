package com.continuum.core.worker.activity

import com.continuum.core.commons.activity.IContinuumNodeActivity
import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.exception.NodeRuntimeException
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.PortData
import com.continuum.core.commons.model.PortDataStatus
import com.continuum.core.commons.node.ProcessNodeModel
import com.continuum.core.commons.node.TriggerNodeModel
import com.continuum.core.commons.prototol.progress.ContinuumNodeActivitySignal
import com.continuum.core.commons.prototol.progress.NodeProgress
import com.continuum.core.commons.prototol.progress.NodeProgressCallback
import com.continuum.core.commons.prototol.progress.StageStatus
import com.continuum.core.commons.utils.NodeInputReader
import com.continuum.core.commons.utils.NodeOutputWriter
import com.continuum.core.commons.workflow.IContinuumWorkflow
import io.temporal.activity.Activity
import io.temporal.spring.boot.ActivityImpl
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
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

/**
 * Temporal activity implementation for executing Continuum workflow nodes.
 *
 * This class is the core execution engine for individual nodes within a Continuum workflow.
 * It implements the [IContinuumNodeActivity] interface and is registered as a Temporal activity
 * that processes nodes from the configured task queue.
 *
 * ## Responsibilities
 * - Discovers and registers all available [ProcessNodeModel] and [TriggerNodeModel] beans
 * - Downloads input data from S3/MinIO storage before node execution
 * - Executes the appropriate node model based on the workflow configuration
 * - Uploads output data to S3/MinIO storage after node execution
 * - Reports node execution progress back to the parent workflow via signals
 * - Handles node execution errors with appropriate retry/fail behavior
 *
 * ## Data Flow
 * 1. Input data is downloaded from S3 to local cache as Parquet files
 * 2. Node model processes the input and writes output to local cache
 * 3. Output Parquet files are uploaded back to S3
 * 4. References to S3 objects are returned to the workflow
 *
 * ## Error Handling
 * - [NodeRuntimeException] with `isRetriable = false` causes immediate failure
 * - Other exceptions trigger Temporal's retry mechanism
 *
 * @property processNodesModelProvider Provider for all registered [ProcessNodeModel] beans
 * @property triggerNodeModelProvider Provider for all registered [TriggerNodeModel] beans
 * @property s3TransferManager AWS S3 transfer manager for efficient file transfers
 * @property cacheBucketName S3 bucket name for storing workflow data
 * @property cacheBucketBasePath Base path within the S3 bucket for workflow data
 * @property cacheStoragePath Local filesystem path for caching workflow data
 * @property progressReportRateLimitMs Minimum milliseconds between progress reports to avoid bloating workflow history (default: 5000ms)
 * @author Continuum Team
 * @since 1.0.0
 * @see IContinuumNodeActivity
 * @see ProcessNodeModel
 * @see TriggerNodeModel
 */
@Component
@ActivityImpl(taskQueues = ["\${continuum.core.worker.node-task-queue:${TaskQueues.ACTIVITY_TASK_QUEUE}}"])
class ContinuumNodeActivity(
  private val processNodesModelProvider: ObjectProvider<ProcessNodeModel>,
  private val triggerNodeModelProvider: ObjectProvider<TriggerNodeModel>,
  private val s3TransferManager: S3TransferManager,
  @Value("\${continuum.core.worker.storage.bucket-name}")
  private val cacheBucketName: String,
  @Value("\${continuum.core.worker.storage.bucket-base-path}")
  private val cacheBucketBasePath: String,
  @Value("\${continuum.core.worker.cache-storage-path}")
  private val cacheStoragePath: Path,
  @Value("\${continuum.core.worker.progress-report-rate-limit-ms:100}")
  private val progressReportRateLimitMs: Long
) : IContinuumNodeActivity {

  /** Map of process node class names to their model instances */
  private val processNodeMap = mutableMapOf<String, ProcessNodeModel>()

  /** Map of trigger node class names to their model instances */
  private val triggerNodeMap = mutableMapOf<String, TriggerNodeModel>()

  companion object {
    /** Logger instance for this class */
    private val LOGGER = LoggerFactory.getLogger(ContinuumNodeActivity::class.java)
  }

  /**
   * Initializes the node maps after bean construction.
   *
   * This method populates the [processNodeMap] and [triggerNodeMap] with all
   * registered node model beans, using their fully qualified class names as keys.
   * This enables fast lookup during workflow execution.
   */
  @PostConstruct
  fun onInit() {
    // Register all process nodes by their class name
    processNodesModelProvider.forEach {
      processNodeMap[it.javaClass.name] = it
    }
    // Register all trigger nodes by their class name
    triggerNodeModelProvider.forEach {
      triggerNodeMap[it.javaClass.name] = it
    }
    LOGGER.info("Registered process nodes: ${processNodeMap.keys}")
    LOGGER.info("Registered trigger nodes: ${triggerNodeMap.keys}")
  }

  /**
   * Executes a single workflow node as a Temporal activity.
   *
   * This is the main entry point for node execution. It performs the following steps:
   * 1. Creates a progress callback to report execution progress to the parent workflow
   * 2. Creates the local cache directory for this node's data
   * 3. Downloads input data from S3 to local storage
   * 4. Executes the appropriate node model (process or trigger)
   * 5. Uploads output data from local storage to S3
   * 6. Returns the node outputs with S3 references
   *
   * @param node The workflow node configuration to execute
   * @param inputs Map of input port IDs to their [PortData] (containing S3 references)
   * @return [IContinuumNodeActivity.NodeActivityOutput] containing the node ID and output port data
   * @throws NodeRuntimeException if the node execution fails with a retriable error
   */
  override fun run(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>
  ): IContinuumNodeActivity.NodeActivityOutput {
    // Create a callback to report progress updates to the parent workflow via Temporal signals
    // Rate-limited to avoid bloating workflow history with too many signals
    val lastReportTime = java.util.concurrent.atomic.AtomicLong(0)
    val lastReportedStages = java.util.concurrent.atomic.AtomicReference<Map<String, StageStatus>?>(null)

    val nodeProgressCallback = object : NodeProgressCallback {
      /**
       * Reports detailed progress including percentage and message.
       * Rate-limited to avoid bloating workflow history - only reports if:
       * - Progress is 0% (start) or 100% (complete)
       * - A stage status has changed (e.g., IN_PROGRESS -> COMPLETED)
       * - Sufficient time has elapsed since the last report (configurable via progressReportRateLimitMs)
       */
      override fun report(nodeProgress: NodeProgress) {
        val now = System.currentTimeMillis()
        val lastTime = lastReportTime.get()

        // Check if any stage status has changed
        val currentStages = nodeProgress.stageStatus
        val previousStages = lastReportedStages.get()
        val stageChanged = currentStages != null && currentStages != previousStages

        // Always report 0% (start), 100% (complete), or stage changes; rate-limit other updates
        val shouldReport = nodeProgress.progressPercentage == 0 ||
                           nodeProgress.progressPercentage == 100 ||
                           stageChanged ||
                           (now - lastTime) >= progressReportRateLimitMs

        if (!shouldReport) {
          LOGGER.debug("Skipping progress report (rate limited) - Node ID: ${node.id}, Progress: ${nodeProgress.progressPercentage}%")
          return
        }

        lastReportTime.set(now)
        lastReportedStages.set(currentStages)
        LOGGER.info("NodeProgressCallback report - Node ID: ${node.id}, Progress: ${nodeProgress.progressPercentage}%, Message: ${nodeProgress.message}")
        // Send progress signal to the parent workflow
        Activity.getExecutionContext().workflowClient.newWorkflowStub(
          IContinuumWorkflow::class.java,
          Activity.getExecutionContext().info.workflowId
        ).updateNodeProgressSignal(
          continuumNodeActivitySignal = ContinuumNodeActivitySignal(
            nodeId = node.id,
            nodeProgress = nodeProgress
          )
        )
      }

      /**
       * Reports progress percentage only.
       */
      override fun report(progressPercentage: Int) {
        report(NodeProgress(progressPercentage))
      }
    }

    // Create local cache directory for this node's input/output files
    Files.createDirectories(cacheStoragePath.resolve("${Activity.getExecutionContext().info.runId}/${node.id}"))
    try {
      // Determine node type and execute the appropriate node model
      if (processNodeMap.containsKey(node.data.nodeModel)) {
        // === PROCESS NODE EXECUTION ===
        LOGGER.info("Downloading input files for node ${node.id} (${node.data.nodeModel})")
        // Download all input port data from S3 to local cache
        val nodeInputs = prepareNodeInputs(node.id, inputs)
        LOGGER.info("Input files downloaded for node ${node.id} (${node.data.nodeModel})")

        // Create output writer for the node to write its results
        val nodeOutputWriter =
          NodeOutputWriter(cacheStoragePath.resolve("${Activity.getExecutionContext().info.runId}/${node.id}"))

        // Execute the process node with inputs and collect outputs
        processNodeMap[node.data.nodeModel]!!.run(
          node = node,
          inputs = nodeInputs,
          nodeOutputWriter = nodeOutputWriter,
          nodeProgressCallback = nodeProgressCallback
        )

        // Upload output files to S3 and get references
        LOGGER.info("Uploading output files for node ${node.id} (${node.data.nodeModel})")
        val nodeOutput = prepareNodeOutputs(
          nodeId = node.id,
          nodeOutputWriter = nodeOutputWriter
        )
        LOGGER.info("Output files uploaded for node ${node.id} (${node.data.nodeModel})")

        return IContinuumNodeActivity.NodeActivityOutput(
          nodeId = node.id,
          outputs = nodeOutput
        )
      } else if (triggerNodeMap.containsKey(node.data.nodeModel)) {
        // === TRIGGER NODE EXECUTION ===
        // Trigger nodes don't have inputs, they generate data (e.g., from external sources)
        val nodeOutputWriter =
          NodeOutputWriter(cacheStoragePath.resolve("${Activity.getExecutionContext().info.runId}/${node.id}"))

        // Execute the trigger node
        triggerNodeMap[node.data.nodeModel]!!.run(
          node,
          nodeOutputWriter = nodeOutputWriter
        )

        return IContinuumNodeActivity.NodeActivityOutput(
          nodeId = node.id,
          outputs = prepareNodeOutputs(
            nodeId = node.id,
            nodeOutputWriter = nodeOutputWriter
          )
        )
      }
    } catch (e: NodeRuntimeException) {
      // Handle node-specific runtime exceptions
      LOGGER.error("Error while executing node ${node.id} (${node.data.nodeModel})", e)
      if (!e.isRetriable) {
        // Non-retriable errors should fail immediately without Temporal retries
        return IContinuumNodeActivity.NodeActivityOutput(
          nodeId = node.id,
          outputs = mapOf(
            "\$error" to PortData(
              tableSpec = emptyList(),
              data = e.message ?: "Unknown error",
              contentType = "text/plain",
              status = PortDataStatus.FAILED
            )
          )
        )
      } else {
        // Retriable errors should trigger Temporal's retry mechanism
        throw e
      }
    }

    // Node model not found - return error output
    return IContinuumNodeActivity.NodeActivityOutput(
      nodeId = node.id,
      outputs = mapOf(
        "\$error" to PortData(
          tableSpec = emptyList(),
          data = "Node model '${node.data.nodeModel}' not found",
          contentType = "text/plain",
          status = PortDataStatus.FAILED
        )
      )
    )
  }

  /**
   * Prepares node inputs by downloading data from S3 to local cache.
   *
   * This method downloads input Parquet files from S3 storage to the local filesystem
   * for processing. Files are cached locally to avoid redundant downloads if they
   * already exist.
   *
   * The S3 key is extracted from the [PortData.data] field by removing the "{remote}"
   * prefix that indicates the data is stored remotely.
   *
   * @param nodeId The ID of the node being executed
   * @param inputs Map of input port IDs to their [PortData] containing S3 references
   * @return Map of input port IDs to [NodeInputReader] instances for reading the data
   */
  fun prepareNodeInputs(
    nodeId: String,
    inputs: Map<String, PortData>
  ): Map<String, NodeInputReader> {
    val workflowRunId = Activity.getExecutionContext().info.runId
    return inputs.mapValues {
      // Construct local file path for the input data
      val filePath = cacheStoragePath.resolve("$workflowRunId/$nodeId/input.${it.key}.parquet")

      if (!Files.exists(filePath)) {
        // File not cached locally - download from S3
        // Remove the "{remote}" prefix to get the actual S3 key
        val destinationKey = "$cacheBucketBasePath/${it.value.data.toString().removePrefix("{remote}")}"

        val uploadTime = measureTimeMillis {
          // Use S3 transfer manager for efficient download with progress logging
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
        // File already exists in local cache - skip download
        LOGGER.info("File '$filePath' already exists, skipping download")
      }

      // Return a reader for the downloaded/cached file
      NodeInputReader(filePath)
    }
  }

  /**
   * Prepares node outputs by uploading local files to S3 storage.
   *
   * This method scans the node's output directory for Parquet files matching the
   * pattern "output.{portId}.parquet", uploads them to S3, and returns [PortData]
   * objects with references to the uploaded files.
   *
   * @param nodeId The ID of the node that produced the outputs
   * @param nodeOutputWriter The output writer containing table specifications
   * @return Map of output port IDs to [PortData] with S3 references
   */
  fun prepareNodeOutputs(
    nodeId: String,
    nodeOutputWriter: NodeOutputWriter
  ): Map<String, PortData> {
    val workflowRunId = Activity.getExecutionContext().info.runId
    val nodeOutputPath = cacheStoragePath.resolve("$workflowRunId/$nodeId")

    // Find all output Parquet files in the node's output directory
    val lisOutputFiles = nodeOutputPath.listDirectoryEntries()
      .filter { it.fileName.toString().startsWith("output.") && it.fileName.toString().endsWith(".parquet") }

    return lisOutputFiles.map {
      // Extract port ID from filename (e.g., "output.port1.parquet" -> "port1")
      val portId = it.fileName.toString().substringAfter("output.").substringBefore(".parquet")

      // Construct S3 key for the output file
      val relativeFileKey = "$workflowRunId/$nodeId/output.$portId.parquet"
      val destinationKey = "$cacheBucketBasePath/$relativeFileKey"

      // Upload the output file to S3 with progress logging
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

      // Create PortData with reference to the uploaded S3 object
      val portData = PortData(
        tableSpec = nodeOutputWriter
          .getTableSpec(portId),
        data = "{remote}${relativeFileKey}",  // Prefix with "{remote}" to indicate S3 storage
        contentType = "application/parquet",
        status = PortDataStatus.SUCCESS
      )
      Pair(portId, portData)
    }.associate { it }
  }
}