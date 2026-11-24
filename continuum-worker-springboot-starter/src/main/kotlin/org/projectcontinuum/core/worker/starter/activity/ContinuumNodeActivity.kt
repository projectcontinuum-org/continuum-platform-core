package org.projectcontinuum.core.worker.starter.activity

import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity
import org.projectcontinuum.core.commons.annotation.ContinuumNode
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.context.ContinuumOwnerContext
import org.projectcontinuum.core.commons.context.ExecutionContext
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
import org.projectcontinuum.core.commons.utils.DefaultFlowVariables
import org.projectcontinuum.core.commons.utils.FlowVariables
import org.projectcontinuum.core.commons.utils.NodeInputReader
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import org.projectcontinuum.core.commons.context.CredentialFetcher
import org.projectcontinuum.core.worker.starter.credential.CredentialFetcherService
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.activity.Activity
import io.temporal.activity.ActivityExecutionContext
import io.temporal.client.ActivityCompletionException
import io.temporal.common.converter.JacksonJsonPayloadConverter
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
 * ## Flow variables
 * `inputs["$variable"]` (inherited flow variables, collected by the orchestrator) is handed to the node through
 * [ExecutionContext.inputFlowVariables] and is never downloaded. `outputs["$variable"]` carries the variables the node
 * set itself plus the run's [DefaultFlowVariables], so it is present for every result, success or `$error`. Inherited
 * variables are never passed back.
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
 * @property maxFlowVariablesSizeBytes Maximum serialized JSON size of the flow variables one node sets; larger fails the node, non-retriable (default: 65536)
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
  private val credentialFetcherService: CredentialFetcherService,
  @param:Value("\${continuum.core.worker.storage.bucket-name}")
  private val cacheBucketName: String,
  @param:Value("\${continuum.core.worker.storage.bucket-base-path}")
  private val cacheBucketBasePath: String,
  @param:Value("\${continuum.core.worker.cache-storage-path}")
  private val cacheStoragePath: Path,
  @param:Value("\${continuum.core.worker.progress-report-rate-limit-ms:5000}")
  private val progressReportRateLimitMs: Long,
  @param:Value("\${continuum.core.worker.heartbeat-interval-ms:60000}")
  private val heartbeatIntervalMs: Long,
  @param:Value("\${continuum.core.worker.flow-variables.max-size-bytes:65536}")
  private val maxFlowVariablesSizeBytes: Long = DEFAULT_MAX_FLOW_VARIABLES_SIZE_BYTES
) : IContinuumNodeActivity {

  companion object {
    private val LOGGER = LoggerFactory.getLogger(ContinuumNodeActivity::class.java)

    /** Default cap on the serialized size of the flow variables one node sets (64 KiB). */
    const val DEFAULT_MAX_FLOW_VARIABLES_SIZE_BYTES = 65536L

    /** Same JSON mapping as the Temporal payload converter (see `AutoConfigure`), used to measure flow variables. */
    private val FLOW_VARIABLES_MAPPER = JacksonJsonPayloadConverter.newDefaultObjectMapper()
      .apply { registerModule(KotlinModule.Builder().build()) }

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
      val processNode = applicationContext.getBean(beanType) as ProcessNodeModel
      validateReservedPortIds(
        nodeClass = beanType,
        portIds = processNode.inputPorts.keys +
          processNode.outputPorts.keys +
          processNode.metadata.inputs.orEmpty().keys +
          processNode.metadata.outputs.orEmpty().keys
      )
      processNodeClassNames.add(beanType.name)
    }
    applicationContext.getBeanNamesForType(TriggerNodeModel::class.java).forEach { beanName ->
      val beanType = applicationContext.getType(beanName)!!
      validateNodeModelAnnotation(beanType)
      val triggerNode = applicationContext.getBean(beanType) as TriggerNodeModel
      validateReservedPortIds(
        nodeClass = beanType,
        portIds = triggerNode.outputPorts.keys + triggerNode.metadata.outputs.orEmpty().keys
      )
      triggerNodeClassNames.add(beanType.name)
    }
    LOGGER.info("Registered process nodes: $processNodeClassNames")
    LOGGER.info("Registered trigger nodes: $triggerNodeClassNames")
  }

  /**
   * Fails startup when a node declares a port id reserved for system keys (`$error`, `$variable`, any id starting
   * with `$`), which would collide with the system entries of the node's `inputs` / `outputs` maps.
   */
  private fun validateReservedPortIds(nodeClass: Class<*>, portIds: Set<String>) {
    val reservedPortIds = portIds.filter { IContinuumNodeActivity.NodeOutputSystemPort.isSystemPort(it) }
    check(reservedPortIds.isEmpty()) {
      "Node model '${nodeClass.name}' declares port id(s) $reservedPortIds. Port ids starting with " +
        "'${IContinuumNodeActivity.NodeOutputSystemPort.SYSTEM_PORT_PREFIX}' are reserved for system keys " +
        "such as \$error and \$variable."
    }
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
    val startTimestamp = System.currentTimeMillis()
    val runStatistics = NodeRunStatistics()
    val workflowRunId = Activity.getExecutionContext().info.runId
    val nodeModel = node.data.nodeModel

    // Create local cache directory for this node's input/output files
    val nodeCachePath = cacheStoragePath.resolve("$workflowRunId/${node.id}")
    Files.createDirectories(nodeCachePath)

    val executionStartTime = AtomicLong(0)
    val cancellation = ActivityCancellation(Thread.currentThread())
    val progressCallback = createProgressCallback(node.id, executionStartTime, cancellation)

    // Start a background heartbeat scheduler to keep the activity alive even when
    // the node doesn't call report() for extended periods (e.g., during long model
    // downloads or training steps). Without this, Temporal would consider the activity
    // dead after the heartbeat timeout and retry it unnecessarily.
    val activityContext = Activity.getExecutionContext()
    val heartbeatScheduler = startHeartbeatScheduler(node.id, activityContext, cancellation)

    // Report BUSY before any input download so the UI sees the node start working
    reportBusy(node.id)

    return try {
      val nodeOutput = when {
        processNodeClassNames.contains(nodeModel) ->
          executeProcessNode(node, inputs, progressCallback, executionStartTime, runStatistics)
        triggerNodeClassNames.contains(nodeModel) ->
          executeTriggerNode(node, inputs, progressCallback, executionStartTime, runStatistics)
        else -> createErrorOutput(node.id, "Node model '$nodeModel' not found")
      }
      cancellation.rethrowIfCancelled()
      withDefaultFlowVariables(
        node = node,
        nodeOutput = nodeOutput,
        startTimestamp = startTimestamp,
        runStatistics = runStatistics
      )
    } catch (e: NodeRuntimeException) {
      cancellation.rethrowIfCancelled()
      withDefaultFlowVariables(
        node = node,
        nodeOutput = handleNodeException(node, e),
        startTimestamp = startTimestamp,
        runStatistics = runStatistics
      )
    } catch (e: Throwable) {
      // Report the cancellation to Temporal, not whatever the interrupted node threw on its way out
      cancellation.rethrowIfCancelled()
      throw e
    } finally {
      heartbeatScheduler.shutdownNow()
      cancellation.close()
    }
  }

  /** Row counts gathered during one node run, for the default flow variables. Null until known. */
  private class NodeRunStatistics {
    var inputRowCounts: Map<String, Long>? = null
    var outputRowCounts: Map<String, Long>? = null
  }

  /**
   * Carries a Temporal cancellation (or timeout / worker shutdown) seen by a heartbeat to the activity thread.
   *
   * Heartbeats are how Temporal delivers cancellation: [ActivityExecutionContext.heartbeat] throws an
   * [ActivityCompletionException]. The background scheduler runs on its own thread, so on its first such
   * exception it interrupts the activity thread — a node blocked in I/O, `Process.waitFor` or `Thread.sleep`
   * stops — and [run] rethrows the exception so Temporal records the cancellation, even when the node
   * swallowed the interrupt or the exception from [NodeProgressCallback.report].
   */
  private class ActivityCancellation(private val activityThread: Thread) {
    private val lock = Any()
    private var exception: ActivityCompletionException? = null
    private var closed = false
    private var interrupted = false

    /** Records [e]; with [interrupt], interrupts the activity thread unless the activity already finished. */
    fun cancel(e: ActivityCompletionException, interrupt: Boolean) = synchronized(lock) {
      if (closed || exception != null) return
      exception = e
      if (interrupt) {
        interrupted = true
        activityThread.interrupt()
      }
    }

    fun rethrowIfCancelled() {
      synchronized(lock) { exception }?.let { throw it }
    }

    /** Ends the activity: no later interrupts; clears the one sent so it does not leak into the next task on this thread. */
    fun close() = synchronized(lock) {
      closed = true
      if (interrupted) Thread.interrupted()
    }
  }

  // ==========================================================================
  // Node Execution Methods
  // ==========================================================================

  /**
   * Executes a process node with the given inputs.
   *
   * The `$variable` system key is read from [inputs] as the node's inherited flow variables and never downloaded.
   * The returned outputs carry `$variable` only when the node set flow variables itself ([run] then adds the
   * [DefaultFlowVariables] to every result, so the activity's outputs always have it); inherited variables are not
   * passed back (the orchestrator collects them from the ancestors).
   */
  private fun executeProcessNode(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>,
    progressCallback: NodeProgressCallback,
    executionStartTime: AtomicLong,
    runStatistics: NodeRunStatistics
  ): IContinuumNodeActivity.NodeActivityOutput {
    val workflowRunId = Activity.getExecutionContext().info.runId
    val nodeModel = node.data.nodeModel
    val inputFlowVariables = FlowVariables.extract(inputs)

    LOGGER.info("Downloading input files for node ${node.id} ($nodeModel)")
    val nodeInputs = prepareNodeInputs(node.id, FlowVariables.withoutSystemPorts(inputs))
    LOGGER.info("Input files downloaded for node ${node.id} ($nodeModel)")
    runStatistics.inputRowCounts = countInputRows(nodeId = node.id, nodeInputs = nodeInputs)

    val nodeOutputWriter = NodeOutputWriter(cacheStoragePath.resolve("$workflowRunId/${node.id}"))

    executionStartTime.set(System.currentTimeMillis())

    val executionContext = createExecutionContext(inputFlowVariables = inputFlowVariables)

    createProcessNode(nodeModel).run(
      node = node,
      inputs = nodeInputs,
      nodeOutputWriter = nodeOutputWriter,
      nodeProgressCallback = progressCallback,
      executionContext = executionContext
    )

    progressCallback.report(NodeProgress(100))

    val emittedFlowVariables = executionContext.getOutputFlowVariables()
    checkFlowVariablesSize(node = node, flowVariables = emittedFlowVariables)

    LOGGER.info("Uploading output files for node ${node.id} ($nodeModel)")
    val nodeOutput = prepareNodeOutputs(node.id, nodeOutputWriter)
    LOGGER.info("Output files uploaded for node ${node.id} ($nodeModel)")
    runStatistics.outputRowCounts = countOutputRows(nodeOutput = nodeOutput, nodeOutputWriter = nodeOutputWriter)

    return IContinuumNodeActivity.NodeActivityOutput(
      nodeId = node.id,
      outputs = withFlowVariables(outputs = nodeOutput, flowVariables = emittedFlowVariables)
    )
  }

  /**
   * Executes a trigger node (no data inputs). Inherited flow variables in [inputs], if any, are still offered to the
   * node; the outputs carry `$variable` only when the node set flow variables itself ([run] adds the
   * [DefaultFlowVariables] afterwards).
   */
  private fun executeTriggerNode(
    node: ContinuumWorkflowModel.Node,
    inputs: Map<String, PortData>,
    progressCallback: NodeProgressCallback,
    executionStartTime: AtomicLong,
    runStatistics: NodeRunStatistics
  ): IContinuumNodeActivity.NodeActivityOutput {
    val workflowRunId = Activity.getExecutionContext().info.runId
    val nodeOutputWriter = NodeOutputWriter(cacheStoragePath.resolve("$workflowRunId/${node.id}"))

    executionStartTime.set(System.currentTimeMillis())

    val executionContext = createExecutionContext(inputFlowVariables = FlowVariables.extract(inputs))

    createTriggerNode(node.data.nodeModel).run(
      node = node,
      nodeOutputWriter = nodeOutputWriter,
      executionContext = executionContext
    )

    progressCallback.report(NodeProgress(100))

    val emittedFlowVariables = executionContext.getOutputFlowVariables()
    checkFlowVariablesSize(node = node, flowVariables = emittedFlowVariables)

    val nodeOutput = prepareNodeOutputs(node.id, nodeOutputWriter)
    runStatistics.outputRowCounts = countOutputRows(nodeOutput = nodeOutput, nodeOutputWriter = nodeOutputWriter)

    return IContinuumNodeActivity.NodeActivityOutput(
      nodeId = node.id,
      outputs = withFlowVariables(outputs = nodeOutput, flowVariables = emittedFlowVariables)
    )
  }

  /**
   * Builds the [ExecutionContext] for one node run. The node fetches whichever credential(s) it needs, by name, on
   * demand via `ExecutionContext.getCredential(name)` — the framework does not scan propertiesUISchema.
   */
  private fun createExecutionContext(inputFlowVariables: Map<String, Any>): ExecutionContext {
    val ownerId = ContinuumOwnerContext.get()
    return ExecutionContext(
      ownerId = ownerId,
      credentialFetcher = if (ownerId != null) {
        CredentialFetcher { name -> credentialFetcherService.fetch(name, ownerId) }
      } else {
        CredentialFetcher { null }
      },
      inputFlowVariables = inputFlowVariables
    )
  }

  // ==========================================================================
  // Flow Variables
  // ==========================================================================

  /**
   * Fails the node (non-retriable) when the flow variables it set serialize to more than [maxFlowVariablesSizeBytes]
   * UTF-8 bytes of JSON, or cannot be serialized at all.
   */
  private fun checkFlowVariablesSize(node: ContinuumWorkflowModel.Node, flowVariables: Map<String, Any>) {
    if (flowVariables.isEmpty()) return
    val serializedSizeBytes = try {
      FLOW_VARIABLES_MAPPER.writeValueAsBytes(flowVariables).size.toLong()
    } catch (e: Exception) {
      throw NodeRuntimeException(
        isRetriable = false,
        workflowId = Activity.getExecutionContext().info.workflowId,
        nodeId = node.id,
        message = "Flow variables of node '${node.id}' cannot be serialized to JSON: ${e.message}"
      )
    }
    if (serializedSizeBytes > maxFlowVariablesSizeBytes) {
      throw NodeRuntimeException(
        isRetriable = false,
        workflowId = Activity.getExecutionContext().info.workflowId,
        nodeId = node.id,
        message = "Flow variables of node '${node.id}' are $serializedSizeBytes bytes, max $maxFlowVariablesSizeBytes " +
          "(continuum.core.worker.flow-variables.max-size-bytes)"
      )
    }
  }

  /** Adds the `$variable` system key to [outputs] when [flowVariables] is non-empty; otherwise returns [outputs] as is. */
  private fun withFlowVariables(
    outputs: Map<String, PortData>,
    flowVariables: Map<String, Any>
  ): Map<String, PortData> {
    if (flowVariables.isEmpty()) return outputs
    return outputs + (IContinuumNodeActivity.NodeOutputSystemPort.VARIABLE.key to FlowVariables.toPortData(flowVariables))
  }

  /**
   * Adds this run's [DefaultFlowVariables] (`<nodeType>_<nodeId>.executionStatus`, `.durationMs`, ...) to
   * `$variable` of [nodeOutput], for successful and failed nodes alike. They are written over a node-set variable of
   * the same name. Not counted against [maxFlowVariablesSizeBytes], which caps what the node sets itself.
   */
  private fun withDefaultFlowVariables(
    node: ContinuumWorkflowModel.Node,
    nodeOutput: IContinuumNodeActivity.NodeActivityOutput,
    startTimestamp: Long,
    runStatistics: NodeRunStatistics
  ): IContinuumNodeActivity.NodeActivityOutput {
    val errorPort = nodeOutput.outputs[IContinuumNodeActivity.NodeOutputSystemPort.ERROR.key]
    val failed = errorPort != null
    val defaultFlowVariables = DefaultFlowVariables.build(
      prefix = DefaultFlowVariables.prefix(nodeModel = node.data.nodeModel, nodeId = node.id),
      failed = failed,
      errorMessage = errorPort?.data?.toString(),
      startTimestamp = startTimestamp,
      endTimestamp = System.currentTimeMillis(),
      inputRowCounts = runStatistics.inputRowCounts,
      outputRowCounts = if (failed) null else runStatistics.outputRowCounts
    )
    val ownFlowVariables = FlowVariables.extract(nodeOutput.outputs)
    return nodeOutput.copy(
      outputs = withFlowVariables(
        outputs = nodeOutput.outputs,
        flowVariables = ownFlowVariables + defaultFlowVariables
      )
    )
  }

  /** Rows per downloaded input port, read from the Parquet footers. A port whose count cannot be read is left out. */
  private fun countInputRows(nodeId: String, nodeInputs: Map<String, NodeInputReader>): Map<String, Long> {
    val inputRowCounts = LinkedHashMap<String, Long>()
    nodeInputs.forEach { (portId, inputReader) ->
      try {
        inputRowCounts[portId] = inputReader.getRowCount()
      } catch (e: Exception) {
        LOGGER.warn("Cannot count the rows of input port '$portId' of node $nodeId: ${e.message}")
      }
    }
    return inputRowCounts
  }

  /** Rows written per uploaded output port (system keys are not ports). */
  private fun countOutputRows(
    nodeOutput: Map<String, PortData>,
    nodeOutputWriter: NodeOutputWriter
  ): Map<String, Long> {
    val outputRowCounts = LinkedHashMap<String, Long>()
    FlowVariables.withoutSystemPorts(nodeOutput).keys.forEach { portId ->
      nodeOutputWriter.getRowCount(portId)?.let { rowCount -> outputRowCounts[portId] = rowCount }
    }
    return outputRowCounts
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
   * A heartbeat failing with [ActivityCompletionException] (activity cancelled, timed out or the worker
   * shutting down) is handed to [cancellation], which stops the node; other failures are logged and retried
   * on the next tick.
   *
   * @param nodeId The ID of the node being executed (for logging)
   * @param activityContext The activity execution context captured on the activity thread
   * @param cancellation Receives a cancellation seen by a heartbeat
   * @return The scheduler, which must be shut down when execution completes
   */
  private fun startHeartbeatScheduler(
    nodeId: String,
    activityContext: ActivityExecutionContext,
    cancellation: ActivityCancellation
  ): ScheduledExecutorService {
    val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
      Thread(runnable, "heartbeat-$nodeId").apply { isDaemon = true }
    }

    scheduler.scheduleAtFixedRate({
      try {
        activityContext.heartbeat("heartbeat for node $nodeId")
        LOGGER.debug("Background heartbeat sent for node: $nodeId")
      } catch (e: ActivityCompletionException) {
        LOGGER.warn("Stopping node $nodeId: ${e.javaClass.simpleName} from heartbeat")
        cancellation.cancel(e, interrupt = true)
        scheduler.shutdown()
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
   * The heartbeat runs on the node's own thread, so a cancellation it reports is thrown straight into the
   * node and also recorded in [cancellation], in case the node catches it.
   *
   * @param nodeId The ID of the node being executed
   * @return A [NodeProgressCallback] instance
   */
  private fun createProgressCallback(
    nodeId: String,
    executionStartTime: AtomicLong,
    cancellation: ActivityCancellation
  ): NodeProgressCallback {
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
        try {
          Activity.getExecutionContext().heartbeat(progressWithDuration)
        } catch (e: ActivityCompletionException) {
          cancellation.cancel(e, interrupt = false)
          throw e
        }

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

  /** Tells the workflow this node's activity has started. Best effort: a failed signal must not fail the node. */
  private fun reportBusy(nodeId: String) {
    try {
      Activity.getExecutionContext().workflowClient.newWorkflowStub(
        IContinuumWorkflow::class.java,
        Activity.getExecutionContext().info.workflowId
      ).updateNodeProgressSignal(
        ContinuumNodeActivitySignal(
          nodeId = nodeId,
          nodeProgress = NodeProgress(progressPercentage = 0),
          nodeStatus = ContinuumWorkflowModel.NodeStatus.BUSY
        )
      )
    } catch (e: Exception) {
      LOGGER.warn("Failed to report BUSY for node $nodeId: ${e.message}")
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

        if (IContinuumNodeActivity.NodeOutputSystemPort.isSystemPort(portId)) {
          throw NodeRuntimeException(
            isRetriable = false,
            workflowId = Activity.getExecutionContext().info.workflowId,
            nodeId = nodeId,
            message = "Node '$nodeId' wrote output port '$portId'. Port ids starting with " +
              "'${IContinuumNodeActivity.NodeOutputSystemPort.SYSTEM_PORT_PREFIX}' are reserved for system keys " +
              "such as \$error and \$variable."
          )
        }

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