package com.continuum.core.worker.workflow

import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.ExecutionStatus
import com.continuum.core.commons.workflow.IContinuumWorkflow
import com.continuum.core.worker.Channels
import com.continuum.core.worker.TestHelper
import com.fasterxml.jackson.databind.ObjectMapper
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.api.enums.v1.WorkflowExecutionStatus
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.common.SearchAttributes
import io.temporal.testing.TestWorkflowEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.messaging.Message
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.time.Duration

@ActiveProfiles("kafka_event")
@SpringBootTest
class ContinuumWorkflowTest {

    @Value("\${continuum.core.worker.cache-bucket-base-path}")
    private lateinit var cacheBucketBasePath: String

    @Value("\${continuum.core.worker.cache-storage-path}")
    private lateinit var cacheStoragePath: Path

    @MockitoBean
    private lateinit var s3TransferManager: S3TransferManager

    @Autowired
    private lateinit var testEnv: TestWorkflowEnvironment

    @Autowired
    private lateinit var workflowClient: WorkflowClient

    @Autowired
    private lateinit var testHelper: TestHelper

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setupEnvironment() {
        // Mock the S3TransferManager downloadFile method
        val fileDownloadMock = mock(FileDownload::class.java)
        whenever(
            fileDownloadMock.completionFuture()
        ).thenReturn(
            CompletableFuture
                .completedFuture(
                    mock(CompletedFileDownload::class.java)
                )
        )
        whenever(
            s3TransferManager.downloadFile(
                any<DownloadFileRequest>()
            )
        ).doAnswer {
            // We just copy file from original path to the destination path
            val downloadFileRequest = it.arguments[0] as DownloadFileRequest
            val sourcePath = downloadFileRequest.objectRequest.key()
                .replace("$cacheBucketBasePath/", "$cacheStoragePath/")
            val destinationPath = downloadFileRequest.destination()
            Files.copy(
                File(sourcePath).toPath(),
                destinationPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            fileDownloadMock
        }

        // Mock the S3TransferManager downloadFile method with
        val fileUploadMock = mock(FileUpload::class.java)
        whenever(
            fileUploadMock.completionFuture()
        ).thenReturn(
            CompletableFuture
                .completedFuture(
                    mock(CompletedFileUpload::class.java)
                )
        )
        whenever(
            s3TransferManager.uploadFile (
                any<UploadFileRequest>()
            )
        ).thenReturn(
            fileUploadMock
        )

        // Register the workflow implementation
        testEnv.registerSearchAttribute(
            IContinuumWorkflow.WORKFLOW_FILE_PATH.name,
            IContinuumWorkflow.WORKFLOW_FILE_PATH.valueType
        )
        testEnv.registerSearchAttribute(
            IContinuumWorkflow.WORKFLOW_STATUS.name,
            IContinuumWorkflow.WORKFLOW_STATUS.valueType
        )
        testEnv.start()
    }

    @AfterEach
    fun resetEnvironment() {
        testEnv.shutdown()
        cacheStoragePath.toFile().deleteRecursively()
    }

    @Test
    fun workflowRunsTest() {
        val workflowModel = loadWorkflow("test-1.cwf.json")
        val workflow = workflowClient.newWorkflowStub(
            IContinuumWorkflow::class.java,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE)
                .setTypedSearchAttributes(SearchAttributes.newBuilder()
                    .set(IContinuumWorkflow.WORKFLOW_FILE_PATH, workflowModel.name)
                    .set(IContinuumWorkflow.WORKFLOW_STATUS, ExecutionStatus.UNKNOWN.value)
                    .build())
                .build()
        )

        val workflowExecution: WorkflowExecution = WorkflowClient.start(
            workflow::start,
            workflowModel
        )

        // Wait for the workflow to complete
        do {
            Thread.sleep(1000)
            val status = getStatus(
                workflowClient,
                workflowExecution
            )
        } while (status == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING)

        val messages = mutableListOf<Message<*>>()
        testHelper.receiveAll(
            Channels.CONTINUUM_WORKFLOW_STATE_CHANGE_EVENT.channelName,
            messages
        )
        assertEquals(
            expected = 4,
            actual = messages.size
        )
    }

    fun loadWorkflow(
        workflowName: String
    ): ContinuumWorkflowModel {
        val workflowFile = this.javaClass.classLoader
            .getResource("test-workflows${File.separator}$workflowName")
            ?: throw IllegalArgumentException("Workflow file not found: $workflowName")

        return objectMapper.readValue(
            workflowFile,
            ContinuumWorkflowModel::class.java
        )
    }

    fun getStatus(client: WorkflowClient, execution: WorkflowExecution): WorkflowExecutionStatus {
        val resp = client.workflowServiceStubs.blockingStub().describeWorkflowExecution(
            DescribeWorkflowExecutionRequest.newBuilder()
                .setNamespace("default")
                .setExecution(execution)
                .build()
        )
        return resp.workflowExecutionInfo.status
    }
}