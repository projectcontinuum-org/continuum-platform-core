package org.projectcontinuum.core.orchestration.workflow

import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.temporal.api.enums.v1.IndexedValueType
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowFailedException
import io.temporal.client.WorkflowOptions
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.failure.ApplicationFailure
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.utils.ValidationHelper
import org.projectcontinuum.core.commons.workflow.IContinuumWorkflow
import org.projectcontinuum.core.orchestration.workflow.fixtures.FakeContinuumNodeActivity
import org.projectcontinuum.core.orchestration.workflow.fixtures.FakeInitializeActivity
import org.projectcontinuum.core.orchestration.workflow.fixtures.WorkflowFixtures
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Node property validation as seen by the orchestration: fixture properties must satisfy the schemas the real nodes
 * register, and a node that fails validation fails the workflow once, without retries, and stops its descendants.
 */
class WorkflowPropertiesValidationTest {

  companion object {
    private const val SPLIT = "org.projectcontinuum.core.worker.node.SplitNodeModel"
    private const val TIME_TRIGGER = "org.projectcontinuum.core.worker.node.TimeTriggerNodeModel"

    /** `SplitNodeModel` schema as registered by `continuum-feature-base` (analytics). */
    private val SPLIT_SCHEMA: Map<String, Any> = mapOf(
      "type" to "object",
      "properties" to mapOf(
        "columnName" to mapOf("type" to "string"),
        "outputs" to mapOf(
          "type" to "array",
          "items" to mapOf(
            "type" to "object",
            "properties" to mapOf("columnName" to mapOf("type" to "string")),
            "required" to listOf("columnName")
          )
        )
      ),
      "required" to listOf("columnName")
    )

    /** `TimeTriggerNodeModel` schema, as carried by `test-1.cwf.json`. */
    private val TIME_TRIGGER_SCHEMA: Map<String, Any> = mapOf(
      "type" to "object",
      "properties" to mapOf(
        "message" to mapOf("type" to "string"),
        "rowCount" to mapOf("type" to "number", "minimum" to 1)
      ),
      "required" to listOf("message", "rowCount")
    )

    private val REGISTERED = mapOf(SPLIT to SPLIT_SCHEMA, TIME_TRIGGER to TIME_TRIGGER_SCHEMA)
  }

  private val activityTaskQueue = "TEST_VALIDATION_NODE_ACTIVITY_TASK_QUEUE"

  private class Env(val testEnv: TestWorkflowEnvironment, val client: WorkflowClient, val fake: FakeContinuumNodeActivity) :
    Closeable {
    override fun close() = testEnv.close()
  }

  private fun newEnv(): Env {
    val mapper = JacksonJsonPayloadConverter.newDefaultObjectMapper().apply { registerModule(KotlinModule.Builder().build()) }
    val testEnv = TestWorkflowEnvironment.newInstance(
      TestEnvironmentOptions.newBuilder()
        .setWorkflowClientOptions(
          WorkflowClientOptions.newBuilder()
            .setDataConverter(DefaultDataConverter.newDefaultInstance().withPayloadConverterOverrides(JacksonJsonPayloadConverter(mapper)))
            .build()
        )
        .registerSearchAttribute("Continuum:ExecutionStatus", IndexedValueType.INDEXED_VALUE_TYPE_INT)
        .registerSearchAttribute("Continuum:WorkflowFileName", IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD)
        .build()
    )
    testEnv.newWorker(TaskQueues.WORKFLOW_TASK_QUEUE).registerWorkflowImplementationTypes(ContinuumWorkflow::class.java)
    val fake = FakeContinuumNodeActivity().apply { registeredSchemas.putAll(REGISTERED) }
    testEnv.newWorker(activityTaskQueue).registerActivitiesImplementations(fake)
    testEnv.newWorker(TaskQueues.ACTIVITY_TASK_QUEUE_INITIALIZE).registerActivitiesImplementations(FakeInitializeActivity(activityTaskQueue))
    testEnv.start()
    return Env(testEnv, testEnv.workflowClient, fake)
  }

  private fun start(env: Env, model: ContinuumWorkflowModel): CompletableFuture<Map<String, Map<String, PortData>>> {
    val stub = env.client.newWorkflowStub(
      IContinuumWorkflow::class.java,
      WorkflowOptions.newBuilder().setWorkflowId("test-${UUID.randomUUID()}").setTaskQueue(TaskQueues.WORKFLOW_TASK_QUEUE).build()
    )
    return WorkflowClient.execute(stub::start, model)
  }

  private fun portData(value: String) = PortData(status = PortDataStatus.SUCCESS, contentType = "text/plain", tableSpec = emptyList(), data = value)

  private fun linear() = WorkflowFixtures.loadWorkflow("/test-workflows/linear-2node.cwf.json")

  /** [model] with node [nodeId]'s properties replaced by [properties]. */
  private fun withProperties(model: ContinuumWorkflowModel, nodeId: String, properties: Map<String, Any>) =
    model.copy(nodes = model.nodes.map { if (it.id == nodeId) it.copy(data = it.data.copy(properties = properties)) else it })

  private fun assertFailsOn(env: Env, model: ContinuumWorkflowModel, failingNode: String): Map<String, Map<*, *>> {
    val e = assertFailsWith<ExecutionException> { start(env, model).get(10, TimeUnit.SECONDS) }
    val failure = (e.cause as WorkflowFailedException).cause as ApplicationFailure
    assertEquals("WorkflowExecutionFailed", failure.type)
    @Suppress("UNCHECKED_CAST")
    val details = failure.details.get(0, Map::class.java) as Map<String, Map<*, *>>
    val error = details.getValue("nodeErrors")[failingNode]
    assertTrue(error.toString().contains("Invalid properties for node '$failingNode'"), "nodeErrors: ${details["nodeErrors"]}")
    assertEquals(1, env.fake.attemptCount[failingNode]!!.get(), "a validation failure must not be retried")
    return details
  }

  // --- fixtures are valid against the registered node schemas ---

  @ParameterizedTest
  @ValueSource(strings = ["/test-workflows/linear-2node.cwf.json"])
  fun `every node in the fixture satisfies its registered schema`(resource: String) {
    val model = WorkflowFixtures.loadWorkflow(resource)

    model.nodes.filter { it.data.nodeModel in REGISTERED }.also { assertTrue(it.isNotEmpty()) }.forEach { node ->
      val violations = ValidationHelper.findSchemaViolations(node.data.properties, REGISTERED.getValue(node.data.nodeModel))
      assertTrue(violations.isEmpty(), "node '${node.id}' (${node.data.nodeModel}): $violations")
    }
  }

  @Test
  fun `the linear fixture runs to completion with registered-schema validation on`() {
    newEnv().use { env ->
      env.fake.successOutputs["2"] = mapOf("output-1" to portData("time"))
      env.fake.successOutputs["3"] = mapOf("output-1" to portData("part1"), "output-2" to portData("part2"))

      val result = start(env, linear()).get(10, TimeUnit.SECONDS)

      assertEquals(setOf("2", "3"), result.keys)
    }
  }

  // --- negative cases ---

  @Test
  fun `split node missing its required column name fails the workflow without retry`() {
    newEnv().use { env ->
      env.fake.successOutputs["2"] = mapOf("output-1" to portData("time"))

      val details = assertFailsOn(env, withProperties(linear(), "3", emptyMap()), failingNode = "3")

      assertTrue(details.getValue("nodeErrors")["3"].toString().contains("columnName"), details.toString())
      assertTrue(details.getValue("nodeToOutputsMap").containsKey("2"), "the upstream success is kept")
    }
  }

  @Test
  fun `split node with a wrongly typed column name fails`() {
    newEnv().use { env ->
      env.fake.successOutputs["2"] = mapOf("output-1" to portData("time"))

      assertFailsOn(env, withProperties(linear(), "3", mapOf("columnName" to 42)), failingNode = "3")
    }
  }

  @Test
  fun `split node with an output entry missing its column name fails`() {
    newEnv().use { env ->
      env.fake.successOutputs["2"] = mapOf("output-1" to portData("time"))
      val properties = mapOf("columnName" to "message", "outputs" to listOf(mapOf("columnName" to "a"), emptyMap<String, Any>()))

      val details = assertFailsOn(env, withProperties(linear(), "3", properties), failingNode = "3")

      assertTrue(details.getValue("nodeErrors")["3"].toString().contains("/outputs/1"), details.toString())
    }
  }

  @Test
  fun `an invalid root node fails first and its downstream node never runs`() {
    newEnv().use { env ->
      // rowCount below the schema minimum of 1
      val model = withProperties(linear(), "2", mapOf("message" to "Logging at", "rowCount" to 0))

      val details = assertFailsOn(env, model, failingNode = "2")

      assertFalse(env.fake.invokedNodeIds.contains("3"), "node 3 must not run after its input failed validation")
      assertFalse(details.getValue("nodeToOutputsMap").containsKey("3"))
    }
  }

  @Test
  fun `a root node missing a required property fails`() {
    newEnv().use { env ->
      assertFailsOn(env, withProperties(linear(), "2", mapOf("message" to "Logging at")), failingNode = "2")
    }
  }
}
