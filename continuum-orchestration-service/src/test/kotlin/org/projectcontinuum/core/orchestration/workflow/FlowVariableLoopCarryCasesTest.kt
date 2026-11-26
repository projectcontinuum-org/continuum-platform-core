package org.projectcontinuum.core.orchestration.workflow

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.projectcontinuum.core.commons.activity.IContinuumNodeActivity.NodeOutputSystemPort
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.model.PortData
import org.projectcontinuum.core.commons.model.PortDataStatus
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.utils.FlowVariables
import org.projectcontinuum.core.orchestration.workflow.fixtures.WorkflowFixtures
import org.projectcontinuum.core.orchestration.workflow.loop.LoopCarriedVariables
import org.projectcontinuum.core.orchestration.workflow.loop.LoopTopology
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Loop-carried flow variables: every case of the shared list `flow-variables/loop-carry-cases.json` (which the
 * workbench's `FlowVariables.ts` spec copies verbatim) against [FlowVariableMerger] and [LoopCarriedVariables].
 */
class FlowVariableLoopCarryCasesTest {

  private companion object {
    const val CASES_RESOURCE = "/flow-variables/loop-carry-cases.json"

    /** SHA-256 of the case file; the workbench copy pins the same value. Update both when the cases change. */
    const val CASES_SHA256 = "f419100f5eb7b188c32dbcbb889749a001166e5c7d102228e150ded0c5779a66"
  }

  private val port = ContinuumWorkflowModel.NodePort(name = "table", contentType = "text/plain")
  private val dataPort = PortData(status = PortDataStatus.SUCCESS, contentType = "text/plain", tableSpec = emptyList(), data = "x")

  private fun casesText(): String {
    val resource = FlowVariableLoopCarryCasesTest::class.java.getResource(CASES_RESOURCE)
      ?: error("$CASES_RESOURCE is missing from the test classpath")
    return resource.readText(charset = Charsets.UTF_8)
  }

  @Test
  fun `the shared case file is the pinned version`() {
    val digest = MessageDigest.getInstance("SHA-256").digest(casesText().toByteArray(charset = Charsets.UTF_8))
    val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }

    assertEquals(CASES_SHA256, hex, "loop-carry-cases.json changed: update the workbench copy and both pins")
  }

  @TestFactory
  fun `every shared loop-carry case`(): List<DynamicTest> {
    val cases = WorkflowFixtures.objectMapper.readTree(casesText()).get("cases")
    val ids = (0 until cases.size()).map { index -> cases.get(index).get("id").asText() }
    assertEquals(ids.size, ids.toSet().size, "case ids must be unique: $ids")
    assertTrue(ids.size >= 8, "expected the full case list, got $ids")
    return (0 until cases.size()).map { index ->
      val case = cases.get(index)
      DynamicTest.dynamicTest(case.get("id").asText()) { runCase(case = case) }
    }
  }

  private fun runCase(case: JsonNode) {
    val workflow = workflowOf(case = case)
    val topology = LoopTopology.of(workflow = workflow)
    val steps = case.get("steps")
    for (stepIndex in 0 until steps.size()) {
      val step = steps.get(stepIndex)
      val label = "${case.get("id").asText()} / ${step.get("label").asText()}"
      val carried = variablesByKey(node = step.get("carried"))
      val outputs = outputsOf(workflow = workflow, own = variablesByKey(node = step.get("own")))
      assertInherited(workflow = workflow, topology = topology, carried = carried, outputs = outputs, step = step, label = label)
      assertCarriedAfter(topology = topology, carried = carried, outputs = outputs, step = step, label = label)
      assertConditionVariables(workflow = workflow, topology = topology, carried = carried, outputs = outputs, step = step, label = label)
    }
  }

  private fun assertInherited(
    workflow: ContinuumWorkflowModel,
    topology: LoopTopology,
    carried: Map<String, Map<String, Any>>,
    outputs: Map<String, Map<String, PortData>>,
    step: JsonNode,
    label: String
  ) {
    variablesByKey(node = step.get("expectInherited")).forEach { (nodeId, expected) ->
      val actual = FlowVariableMerger.collectInputVariables(
        continuumWorkflow = workflow,
        node = workflow.nodes.single { node -> node.id == nodeId },
        nodeToOutputsMap = outputs,
        loopTopology = topology,
        carriedByLoopId = carried
      ).variables
      assertSameVariables(expected = expected, actual = actual, label = "$label: inherited by $nodeId")
    }
  }

  private fun assertCarriedAfter(
    topology: LoopTopology,
    carried: Map<String, Map<String, Any>>,
    outputs: Map<String, Map<String, PortData>>,
    step: JsonNode,
    label: String
  ) {
    variablesByKey(node = step.get("expectCarriedAfter")).forEach { (loopId, expected) ->
      val actual = LoopCarriedVariables.afterIteration(
        loopId = loopId,
        loopTopology = topology,
        previouslyCarried = carried[loopId].orEmpty(),
        nodeToOutputsMap = outputs
      )
      assertSameVariables(expected = expected, actual = actual, label = "$label: carried by $loopId")
    }
  }

  private fun assertConditionVariables(
    workflow: ContinuumWorkflowModel,
    topology: LoopTopology,
    carried: Map<String, Map<String, Any>>,
    outputs: Map<String, Map<String, PortData>>,
    step: JsonNode,
    label: String
  ) {
    variablesByKey(node = step.get("expectConditionVariablesBefore")).forEach { (loopId, expected) ->
      val actual = FlowVariableMerger.endOfIterationVariables(
        continuumWorkflow = workflow,
        loopId = loopId,
        nodeToOutputsMap = outputs,
        loopTopology = topology,
        carriedByLoopId = carried
      ).variables
      assertSameVariables(expected = expected, actual = actual, label = "$label: condition of $loopId before")
    }
    variablesByKey(node = step.get("expectConditionVariablesAfter")).forEach { (loopId, expected) ->
      val carriedAfter = LoopCarriedVariables.afterIteration(
        loopId = loopId,
        loopTopology = topology,
        previouslyCarried = carried[loopId].orEmpty(),
        nodeToOutputsMap = outputs
      )
      val actual = FlowVariableMerger.endOfIterationVariables(
        continuumWorkflow = workflow,
        loopId = loopId,
        nodeToOutputsMap = outputs,
        loopTopology = topology,
        carriedByLoopId = carried + (loopId to carriedAfter)
      ).variables
      assertSameVariables(expected = expected, actual = actual, label = "$label: condition of $loopId after")
    }
  }

  private fun assertSameVariables(expected: Map<String, Any>, actual: Map<String, Any>, label: String) {
    assertTrue(FlowVariables.valuesEqual(first = expected, second = actual), "$label: expected $expected but was $actual")
  }

  private fun workflowOf(case: JsonNode): ContinuumWorkflowModel {
    val caseNodes = case.get("nodes")
    val nodes = (0 until caseNodes.size()).map { index -> nodeOf(caseNode = caseNodes.get(index)) }
    val caseEdges = case.get("edges")
    val edges = (0 until caseEdges.size()).map { index ->
      val caseEdge = caseEdges.get(index)
      ContinuumWorkflowModel.Edge(
        id = "${caseEdge.get("source").asText()}-${caseEdge.get("target").asText()}",
        source = caseEdge.get("source").asText(),
        target = caseEdge.get("target").asText(),
        sourceHandle = "out",
        targetHandle = caseEdge.get("targetHandle").asText()
      )
    }
    return ContinuumWorkflowModel(id = case.get("id").asText(), name = "loop-carry-case", nodes = nodes, edges = edges)
  }

  private fun nodeOf(caseNode: JsonNode): ContinuumWorkflowModel.Node {
    val id = caseNode.get("id").asText()
    val parent = caseNode.get("parent")?.asText()
    val isLoop = caseNode.get("loop")?.asBoolean() ?: false
    val inputPortIds = caseNode.get("inputs")?.let { inputs -> (0 until inputs.size()).map { index -> inputs.get(index).asText() } }
    val data = if (isLoop) {
      LoopNodeDescriptor.nodeData
    } else {
      ContinuumWorkflowModel.NodeData(
        description = id,
        title = id,
        nodeModel = "test.node.$id",
        inputs = inputPortIds?.associateWith { port },
        outputs = mapOf("out" to port)
      )
    }
    return ContinuumWorkflowModel.Node(
      id = id,
      type = if (isLoop) LoopNode.NODE_TYPE else "BaseNode",
      position = ContinuumWorkflowModel.Position(x = 0.0, y = 0.0),
      data = data,
      width = 1,
      height = 1,
      selected = false,
      parentNode = parent
    )
  }

  /** Every non-loop node has completed, with its own variables of this step (none when [own] has no entry). */
  private fun outputsOf(workflow: ContinuumWorkflowModel, own: Map<String, Map<String, Any>>): Map<String, Map<String, PortData>> =
    workflow.nodes
      .filterNot { node -> LoopNode.isLoop(node = node) }
      .associate { node ->
        val ownVariables = own[node.id].orEmpty()
        val outputs = if (ownVariables.isEmpty()) {
          mapOf("out" to dataPort)
        } else {
          mapOf("out" to dataPort, NodeOutputSystemPort.VARIABLE.key to FlowVariables.toPortData(ownVariables))
        }
        node.id to outputs
      }

  private fun variablesByKey(node: JsonNode?): Map<String, Map<String, Any>> {
    if (node == null) return emptyMap()
    @Suppress("UNCHECKED_CAST")
    val converted = WorkflowFixtures.objectMapper.convertValue(node, Map::class.java) as Map<String, Map<String, Any>>
    return converted
  }
}
