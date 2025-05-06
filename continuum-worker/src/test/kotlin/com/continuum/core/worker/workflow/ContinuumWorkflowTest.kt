package com.continuum.core.worker.workflow

import com.continuum.core.commons.constant.TaskQueues
import com.continuum.core.commons.model.ContinuumWorkflowModel
import com.continuum.core.commons.model.ExecutionStatus
import com.continuum.core.commons.node.TriggerNodeModel
import com.continuum.core.commons.workflow.IContinuumWorkflow
import com.continuum.core.worker.node.TimeTriggerNodeModel
import com.fasterxml.jackson.databind.ObjectMapper
import io.temporal.api.common.v1.WorkflowExecution
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import io.temporal.common.SearchAttributes
import io.temporal.testing.TestWorkflowEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import java.time.Duration

@SpringBootTest
class ContinuumWorkflowTest {

    @Autowired
    private lateinit var testEnv: TestWorkflowEnvironment

    @Autowired
    private lateinit var workflowClient: WorkflowClient

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setupEnvironment() {
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

        testEnv.sleep(
            Duration.ofMinutes(10)
        )
        // Wait for the workflow to complete
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
}