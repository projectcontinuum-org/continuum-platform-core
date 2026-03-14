package org.projectcontinuum.core.worker.starter.activity

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.annotation.ContinuumNode
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.node.ProcessNodeModel
import org.projectcontinuum.core.commons.node.TriggerNodeModel
import org.projectcontinuum.core.commons.protocol.progress.ContinuumNodeActivitySignal
import org.projectcontinuum.core.commons.protocol.progress.NodeProgress
import org.projectcontinuum.core.commons.protocol.progress.NodeProgressCallback
import org.projectcontinuum.core.commons.protocol.progress.StageStatus
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import io.temporal.activity.Activity
import io.temporal.activity.ActivityExecutionContext
import io.temporal.spring.boot.ActivityImpl
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
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
 * @property applicationContext Spring application context for on-demand prototype bean creation
 * @property s3TransferManager AWS S3 transfer manager for efficient file transfers
 * @property cacheBucketName S3 bucket name for storing workflow data
 * @property cacheBucketBasePath Base path within the S3 bucket for workflow data
 * @property cacheStoragePath Local filesystem path for caching workflow data
 * @property progressReportRateLimitMs Minimum milliseconds between progress reports to avoid bloating workflow history (default: 100ms)
 * @property heartbeatIntervalMs Interval in milliseconds for background heartbeats to keep long-running activities alive (default: 60000ms)
 * @author Continuum Team
 * @since 1.0.0
 * @see IContinuumNodeActivity
 * @see ProcessNodeModel
 * @see TriggerNodeModel
 */
@Component
@ActivityImpl(taskQueues = ["\${continuum.core.worker.node-task-queue:${TaskQueues.ACTIVITY_TASK_QUEUE}}"])
class ContinuumNodeActivity(
  private val applicationContext: ApplicationContext,
  private val s3TransferManager: S3TransferManager,
  @param:Value("\${continuum.core.worker.storage.bucket-name}")
  private val cacheBucketName: String,
  @param:Value("\${continuum.core.worker.storage.bucket-base-path}")
  private val cacheBucketBasePath: String,
  @param:Value("\${continuum.core.worker.cache-storage-path}")
  private val cacheStoragePath: Path,
  @param:Value("\${continuum.core.worker.progress-report-rate-limit-ms:5000}")
  private val progressReportRateLimitMs: Long,
  @param:Value("\${continuum.core.worker.heartbeat-interval-ms:60000}")
  private val heartbeatIntervalMs: Long
) : IContinuumNodeActivity {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(ContinuumNodeActivity::class.java)

    /** Prefix used to indicate data is stored remotely in S3 */
    private const val REMOTE_DATA_PREFIX = "{remote}"

    /** Output file prefix pattern */
    private const val OUTPUT_FILE_PREFIX = "output."

    /** Parquet file extension */
    private const val PARQUET_EXTENSION = ".parquet"
  }

  /** Set of registered process node class names */
  private val processNodeClassNames = mutableSetOf<String>()

  /** Set of registered trigger node class names */
  private val triggerNodeClassNames = mutableSetOf<String>()

  /**
   * Initializes the node class name registries after bean construction.
   *
   * Populates [processNodeClassNames] and [triggerNodeClassNames] with the fully qualified
   * class names of all registered node model beans for fast lookup during workflow execution.
   * Actual bean instances are created on-demand via [ApplicationContext] to support prototype scope.
   */
  @PostConstruct
  fun onInit() {
    applicationContext.getBeanNamesForType(ProcessNodeModel::class.java).forEach { beanName ->
      val beanType = applicationContext.getType(beanName)!!
      validateNodeModelAnnotation(beanType)
      processNodeClassNames.add(beanType.name)
    }
    applicationContext.getBeanNamesForType(TriggerNodeModel::class.java).forEach { beanName ->
      val beanType = applicationContext.getType(beanName)!!
      validateNodeModelAnnotation(beanType)
      triggerNodeClassNames.add(beanType.name)
    }
    LOGGER.info("Registered process nodes: $processNodeClassNames")
    LOGGER.info("Registered trigger nodes: $triggerNodeClassNames")
  }

  /**
   * Validates that the given node model class is annotated with [ContinuumNode].
   *
   * This ensures proper prototype scoping and component registration for all node models.
   * Throws [IllegalStateException] at startup if a node model is missing the annotation,
   * preventing silent misconfiguration at runtime.
   */
  private fun validateNodeModelAnnotation(nodeClass: Class<*>) {
    val hasAnnotation = nodeClass.isAnnotationPresent(ContinuumNode::class.java)
    check(hasAnnotation) {
      "Node model '${nodeClass.name}' must be annotated with @ContinuumNode. " +
        "This annotation is required for proper prototype scope and Spring component registration."
    }
  }

  /**
   * Creates a new instance of a [ProcessNodeModel] by its fully qualified class name.
   * Uses [ApplicationContext.getBean] to leverage Spring's prototype scope, ensuring
   * each activity execution gets a fresh bean instance.
   */
  private fun createProcessNode(nodeModel: String): ProcessNodeModel {
    val nodeClass = Class.forName(nodeModel)
    return applicationContext.getBean(nodeClass) as ProcessNodeModel
  }

  /**
   * Creates a new instance of a [TriggerNodeModel] by its fully qualified class name.
   * Uses [ApplicationContext.getBean] to leverage Spring's prototype scope, ensuring
   * each activity execution gets a fresh bean instance.
   */
  private fun createTriggerNode(nodeModel: String): TriggerNodeModel {
    val nodeClass = Class.forName(nodeModel)
    return applicationContext.getBean(nodeClass) as TriggerNodeModel
  }

  /**
   * Executes a single workflow node as a Temporal activity.
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
    val workflowRunId = Activity.getExecutionContext().info.runId
    val nodeModel = node.data.nodeModel

    // Create local cache directory for this node's input/output files
    val nodeCachePath = cacheStoragePath.resolve("$workflowRunId/${node.id}")
    Files.createDirectories(nodeCachePath)

    val executionStartTime = AtomicLong(0)
    val progressCallback = createProgressCallback(node.id, executionStartTime)

    // Start a background heartbeat scheduler to keep the activity alive even when
    // the node doesn't call report() for extended periods (e.g., during long model
    // downloads or training steps). Without this, Temporal would consider the activity
    // dead after the heartbeat timeout and retry it unnecessarily.
    val activityContext = Activity.getExecutionContext()
    val heartbeatScheduler = startHeartbeatScheduler(node.id, activityContext)

    return try {
      when {
        processNodeClassNames.contains(nodeModel) -> executeProcessNode(node, inputs, progressCallback, executionStartTime)
        triggerNodeClassNames.contains(nodeModel) -> executeTriggerNode(node, progressCallback, executionStartTime)
        else -> createErrorOutput(node.id, "Node model '$nodeModel' not found")
      }
    } catch (e: NodeRuntimeException) {
      handleNodeException(node, e)
    } finally {
      heartbeatScheduler.shutdownNow()
    }
  }

  // ==========================================================================
  // Node Execution Methods
  // ==========================================================================

  /**
   * Executes a process node with the given inputs.
   */
  private fun executeProcessNode(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>,
    progressCallback: NodeProgressCallback,
    executionStartTime: AtomicLong
  ): IContinuumNodeActivity.NodeActivityOutput {
    val workflowRunId = Activity.getExecutionContext().info.runId
    val nodeModel = node.data.nodeModel

    LOGGER.info("Downloading input files for node ${node.id} ($nodeModel)")
    val nodeInputs = prepareNodeInputs(node.id, inputs)
    LOGGER.info("Input files downloaded for node ${node.id} ($nodeModel)")

    val nodeOutputWriter = NodeOutputWriter(cacheStoragePath.resolve("$workflowRunId/${node.id}"))

    executionStartTime.set(System.currentTimeMillis())

    createProcessNode(nodeModel).run(
      node = node,
      inputs = nodeInputs,
      nodeOutputWriter = nodeOutputWriter,
      nodeProgressCallback = progressCallback
    )

    progressCallback.report(NodeProgress(100))

    LOGGER.info("Uploading output files for node ${node.id} ($nodeModel)")
    val nodeOutput = prepareNodeOutputs(node.id, nodeOutputWriter)
    LOGGER.info("Output files uploaded for node ${node.id} ($nodeModel)")

    return IContinuumNodeActivity.NodeActivityOutput(nodeId = node.id, outputs = nodeOutput)
  }

  /**
   * Executes a trigger node (no inputs required).
   */
  private fun executeTriggerNode(
    node: ContinuumWorkflowModel.Node,
    progressCallback: NodeProgressCallback,
    executionStartTime: AtomicLong
  ): IContinuumNodeActivity.NodeActivityOutput {
    val workflowRunId = Activity.getExecutionContext().info.runId
    val nodeOutputWriter = NodeOutputWriter(cacheStoragePath.resolve("$workflowRunId/${node.id}"))

    executionStartTime.set(System.currentTimeMillis())

    createTriggerNode(node.data.nodeModel).run(node, nodeOutputWriter = nodeOutputWriter)

    progressCallback.report(NodeProgress(100))

    val nodeOutput = prepareNodeOutputs(node.id, nodeOutputWriter)

    return IContinuumNodeActivity.NodeActivityOutput(
      nodeId = node.id,
      outputs = nodeOutput
    )
  }

  // ==========================================================================
  // Heartbeat
  // ==========================================================================

  /**
   * Starts a background scheduler that sends periodic heartbeats to Temporal.
   *
   * This ensures the activity stays alive even during long-running operations
   * where the node doesn't call [NodeProgressCallback.report] for extended
   * periods (e.g., downloading a large model, long training steps).
   *
   * The heartbeat interval is configured via [heartbeatIntervalMs] and should
   * be less than the heartbeat timeout set on the activity options.
   *
   * The caller is responsible for calling [ScheduledExecutorService.shutdownNow]
   * when the activity completes.
   *
   * @param nodeId The ID of the node being executed (for logging)
   * @param activityContext The activity execution context captured on the activity thread
   * @return The scheduler, which must be shut down when execution completes
   */
  private fun startHeartbeatScheduler(
    nodeId: String,
    activityContext: ActivityExecutionContext
  ): ScheduledExecutorService {
    val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
      Thread(runnable, "heartbeat-$nodeId").apply { isDaemon = true }
    }

    scheduler.scheduleAtFixedRate({
      try {
        activityContext.heartbeat("heartbeat for node $nodeId")
        LOGGER.debug("Background heartbeat sent for node: $nodeId")
      } catch (e: Exception) {
        LOGGER.warn("Failed to send background heartbeat for node $nodeId: ${e.message}")
      }
    }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS)

    return scheduler
  }

  // ==========================================================================
  // Progress Callback
  // ==========================================================================

  /**
   * Creates a rate-limited progress callback for reporting node execution progress.
   *
   * The callback is rate-limited to avoid bloating Temporal workflow history.
   * It always reports:
   * - 0% (start) and 100% (completion)
   * - Stage status changes
   * - Intermediate updates only if [progressReportRateLimitMs] has elapsed
   *
   * @param nodeId The ID of the node being executed
   * @return A [NodeProgressCallback] instance
   */
  private fun createProgressCallback(nodeId: String, executionStartTime: AtomicLong): NodeProgressCallback {
    val lastReportTime = AtomicLong(0)
    val lastReportedStages = AtomicReference<Map<String, StageStatus>?>(null)

    return object : NodeProgressCallback {
      override fun report(nodeProgress: NodeProgress) {
        val now = System.currentTimeMillis()
        val startTime = executionStartTime.get()
        val elapsed = if (startTime > 0) now - startTime else 0

        // Preserve last known stage status when reporting 100% without stage info
        val effectiveStageStatus = if (nodeProgress.progressPercentage == 100 && nodeProgress.stageStatus == null) {
          lastReportedStages.get()
        } else {
          nodeProgress.stageStatus
        }
        val progressWithDuration = nodeProgress.copy(
          totalDurationMs = elapsed,
          stageStatus = effectiveStageStatus
        )

        // Always send a heartbeat to Temporal to keep the activity alive.
        // This is independent of rate limiting - heartbeats are cheap and
        // prevent Temporal from thinking the activity is dead.
        Activity.getExecutionContext().heartbeat(progressWithDuration)

        val lastTime = lastReportTime.get()
        val currentStages = progressWithDuration.stageStatus
        val previousStages = lastReportedStages.get()
        val stageChanged = currentStages != null && currentStages != previousStages

        val shouldReport = progressWithDuration.progressPercentage == 0 ||
                           progressWithDuration.progressPercentage == 100 ||
                           stageChanged ||
                           (now - lastTime) >= progressReportRateLimitMs

        if (!shouldReport) {
          LOGGER.debug("Skipping progress report (rate limited) - Node: $nodeId, Progress: ${progressWithDuration.progressPercentage}%")
          return
        }

        lastReportTime.set(now)
        lastReportedStages.set(currentStages)
        LOGGER.info("Progress report - Node: $nodeId, Progress: ${progressWithDuration.progressPercentage}%, Message: ${progressWithDuration.message}")

        sendProgressSignal(nodeId, progressWithDuration)
      }

      override fun report(progressPercentage: Int) {
        report(NodeProgress(progressPercentage))
      }
    }
  }

  /**
   * Sends a progress signal to the parent workflow.
   */
  private fun sendProgressSignal(nodeId: String, nodeProgress: NodeProgress) {
    Activity.getExecutionContext().workflowClient.newWorkflowStub(
      IContinuumWorkflow::class.java,
      Activity.getExecutionContext().info.workflowId
    ).updateNodeProgressSignal(
      continuumNodeActivitySignal = ContinuumNodeActivitySignal(
        nodeId = nodeId,
        nodeProgress = nodeProgress
      )
    )
  }

  // ==========================================================================
  // Data Transfer Methods
  // ==========================================================================

  /**
   * Prepares node inputs by downloading data from S3 to local cache.
   *
   * @param nodeId The ID of the node being executed
   * @param inputs Map of input port IDs to their [PortData] containing S3 references
   * @return Map of input port IDs to [NodeInputReader] instances for reading the data
   */
  private fun prepareNodeInputs(nodeId: String, inputs: Map<String, PortData>): Map<String, NodeInputReader> {
    val workflowRunId = Activity.getExecutionContext().info.runId

    return inputs.mapValues { (portId, portData) ->
      val filePath = cacheStoragePath.resolve("$workflowRunId/$nodeId/input.$portId$PARQUET_EXTENSION")

      if (!Files.exists(filePath)) {
        downloadFromS3(portData.data.toString().removePrefix(REMOTE_DATA_PREFIX), filePath)
      } else {
        LOGGER.info("File '$filePath' already exists, skipping download")
      }

      NodeInputReader(filePath)
    }
  }

  /**
   * Prepares node outputs by uploading local files to S3 storage.
   *
   * @param nodeId The ID of the node that produced the outputs
   * @param nodeOutputWriter The output writer containing table specifications
   * @return Map of output port IDs to [PortData] with S3 references
   */
  private fun prepareNodeOutputs(nodeId: String, nodeOutputWriter: NodeOutputWriter): Map<String, PortData> {
    val workflowRunId = Activity.getExecutionContext().info.runId
    val nodeOutputPath = cacheStoragePath.resolve("$workflowRunId/$nodeId")

    return nodeOutputPath.listDirectoryEntries()
      .filter { it.fileName.toString().startsWith(OUTPUT_FILE_PREFIX) && it.fileName.toString().endsWith(PARQUET_EXTENSION) }
      .associate { outputFile ->
        val portId = outputFile.fileName.toString()
          .removePrefix(OUTPUT_FILE_PREFIX)
          .removeSuffix(PARQUET_EXTENSION)

        val relativeFileKey = "$workflowRunId/$nodeId/$OUTPUT_FILE_PREFIX$portId$PARQUET_EXTENSION"
        uploadToS3(outputFile, relativeFileKey)

        portId to PortData(
          tableSpec = nodeOutputWriter.getTableSpec(portId),
          data = "$REMOTE_DATA_PREFIX$relativeFileKey",
          contentType = "application/parquet",
          status = PortDataStatus.SUCCESS
        )
      }
  }

  /**
   * Downloads a file from S3 to the local filesystem.
   */
  private fun downloadFromS3(s3Key: String, destination: Path) {
    val fullKey = "$cacheBucketBasePath/$s3Key"
    val downloadTime = measureTimeMillis {
      s3TransferManager.downloadFile(
        DownloadFileRequest.builder()
          .getObjectRequest(GetObjectRequest.builder().bucket(cacheBucketName).key(fullKey).build())
          .destination(destination)
          .addTransferListener(LoggingTransferListener.create())
          .build()
      ).completionFuture().get()
    }
    LOGGER.info("Downloaded '$destination' in ${downloadTime}ms")
  }

  /**
   * Uploads a file to S3 storage.
   */
  private fun uploadToS3(source: Path, relativeKey: String) {
    val fullKey = "$cacheBucketBasePath/$relativeKey"
    val uploadTime = measureTimeMillis {
      s3TransferManager.uploadFile(
        UploadFileRequest.builder()
          .putObjectRequest(PutObjectRequest.builder().bucket(cacheBucketName).key(fullKey).build())
          .source(source)
          .addTransferListener(LoggingTransferListener.create())
          .build()
      ).completionFuture().get()
    }
    LOGGER.info("Uploaded '$source' in ${uploadTime}ms")
  }

  // ==========================================================================
  // Error Handling
  // ==========================================================================

  /**
   * Handles node execution exceptions.
   */
  private fun handleNodeException(node: ContinuumWorkflowModel.Node, e: NodeRuntimeException): IContinuumNodeActivity.NodeActivityOutput {
    LOGGER.error("Error executing node ${node.id} (${node.data.nodeModel})", e)

    return if (!e.isRetriable) {
      createErrorOutput(node.id, e.message)
    } else {
      throw e
    }
  }

  /**
   * Creates an error output for a node.
   */
  private fun createErrorOutput(nodeId: String, errorMessage: String): IContinuumNodeActivity.NodeActivityOutput {
    return IContinuumNodeActivity.NodeActivityOutput(
      nodeId = nodeId,
      outputs = mapOf(
        "\$error" to PortData(
          tableSpec = emptyList(),
          data = errorMessage,
          contentType = "text/plain",
          status = PortDataStatus.FAILED
        )
      )
    )
  }
}