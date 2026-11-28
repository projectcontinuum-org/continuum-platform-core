package org.projectcontinuum.core.commons.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionResultType
import org.projectcontinuum.core.commons.validation.loop.LoopConditionValidator

/**
 * The loop condition rule of [WorkflowValidator] with a scripted condition validator (this module cannot use the CEL
 * engine): message and location of the error, the warning for a condition that reads no variable, and no second
 * issue for a property the schema already rejected.
 */
class WorkflowValidatorLoopConditionTest {

  private val table = ContinuumWorkflowModel.NodePort(name = "table", contentType = "application/x-parquet")
  private val step = ContinuumWorkflowModel.NodeData(
    description = "",
    title = "Step",
    nodeModel = STEP,
    inputs = mapOf("in" to table),
    outputs = mapOf("out" to table)
  )

  private companion object {
    const val STEP = "org.test.Step"
  }

  /** Records the texts it was asked about and answers from [answers] (by text), valid with `hasMore` read otherwise. */
  private class ScriptedConditionValidator(private val answers: Map<String, LoopConditionCheckResult>) : LoopConditionValidator {
    val checkedTexts = mutableListOf<String>()

    override fun check(text: String): LoopConditionCheckResult {
      checkedTexts += text
      return answers[text] ?: LoopConditionCheckResult.Valid(resultType = LoopConditionResultType.BOOL, readVariables = setOf("hasMore"))
    }
  }

  private val descriptors = NodeDescriptorSource { nodeModel ->
    when (nodeModel) {
      STEP -> step
      LoopNode.NODE_MODEL -> LoopNodeDescriptor.nodeData
      else -> null
    }
  }

  private fun validate(
    conditionValidator: LoopConditionValidator?,
    loopProperties: Map<String, Any?>,
    mode: ValidationMode = ValidationMode.RUN
  ): WorkflowValidationResult = WorkflowValidator(descriptors = descriptors, conditionValidator = conditionValidator).validate(
    nodes = listOf(
      GraphNode(key = "L", nodeId = LoopNode.NODE_MODEL, properties = loopProperties, type = LoopNode.NODE_TYPE),
      GraphNode(key = "a", nodeId = STEP, properties = emptyMap(), parent = "L"),
      GraphNode(key = "b", nodeId = STEP, properties = emptyMap(), parent = "L")
    ),
    edges = listOf(GraphEdge(from = "a", fromPort = "out", to = "b", toPort = "in")),
    mode = mode
  )

  private fun conditionIssues(issues: List<WorkflowIssue>) = issues.filter { issue -> issue.property == "condition" }

  @Test
  fun `an invalid condition is one error on the loop's condition with the validator's message`() {
    val conditionValidator = ScriptedConditionValidator(
      answers = mapOf(
        "now() > 1" to LoopConditionCheckResult.Invalid(
          code = LoopConditionIssueCode.CHECK_ERROR,
          message = "does not type-check: undeclared reference to 'now' (in container '') (column 4)",
          column = 4
        )
      )
    )

    val result = validate(conditionValidator = conditionValidator, loopProperties = LoopNodeDescriptor.defaultProperties + ("condition" to "now() > 1"))

    val error = conditionIssues(issues = result.errors).single()
    assertEquals(1, result.errors.size, result.errors.toString())
    assertEquals("L", error.node)
    assertEquals("Loop 'L': the condition does not type-check: undeclared reference to 'now' (in container '') (column 4).", error.message)
  }

  @Test
  fun `a valid condition that reads no flow variable is the one condition warning`() {
    val conditionValidator = ScriptedConditionValidator(
      answers = mapOf("true" to LoopConditionCheckResult.Valid(resultType = LoopConditionResultType.BOOL, readVariables = emptySet()))
    )

    val result = validate(conditionValidator = conditionValidator, loopProperties = LoopNodeDescriptor.defaultProperties + ("condition" to "true"))

    assertTrue(result.valid, result.errors.toString())
    val warning = conditionIssues(issues = result.warnings).single()
    assertEquals(
      "Loop 'L': the condition reads no flow variable, so it never changes; the loop runs until maxIterations and then follows onLimit.",
      warning.message
    )
    assertEquals("L", warning.node)
  }

  @Test
  fun `a condition the schema rejected is not checked again`() {
    listOf("", "x".repeat(2049)).forEach { condition ->
      ValidationMode.entries.forEach { mode ->
        val conditionValidator = ScriptedConditionValidator(answers = emptyMap())

        val result = validate(conditionValidator = conditionValidator, loopProperties = LoopNodeDescriptor.defaultProperties + ("condition" to condition), mode = mode)

        assertEquals(1, conditionIssues(issues = result.errors).size, "$mode, ${condition.length} characters: ${result.errors}")
        assertTrue(conditionValidator.checkedTexts.isEmpty(), "the validator was not asked: ${conditionValidator.checkedTexts}")
      }
    }
  }

  @Test
  fun `authoring mode checks the default condition when none is given, run mode checks what was sent`() {
    val authoringValidator = ScriptedConditionValidator(answers = emptyMap())
    val runValidator = ScriptedConditionValidator(answers = emptyMap())

    validate(conditionValidator = authoringValidator, loopProperties = mapOf("maxIterations" to 3), mode = ValidationMode.AUTHORING)
    validate(conditionValidator = runValidator, loopProperties = LoopNodeDescriptor.defaultProperties + ("condition" to "vars[\"x\"]"))

    assertEquals(listOf(LoopNodeDescriptor.DEFAULT_CONDITION), authoringValidator.checkedTexts)
    assertEquals(listOf("vars[\"x\"]"), runValidator.checkedTexts)
  }

  @Test
  fun `without a condition validator only the schema checks the condition`() {
    val result = validate(conditionValidator = null, loopProperties = LoopNodeDescriptor.defaultProperties + ("condition" to "now() > 1"))

    assertTrue(result.valid, result.errors.toString())
    assertTrue(conditionIssues(issues = result.warnings).isEmpty())
  }

  @Test
  fun `a workflow without loops never asks the condition validator`() {
    val conditionValidator = ScriptedConditionValidator(answers = emptyMap())

    val result = WorkflowValidator(descriptors = descriptors, conditionValidator = conditionValidator).validate(
      nodes = listOf(GraphNode(key = "a", nodeId = STEP, properties = emptyMap()), GraphNode(key = "b", nodeId = STEP, properties = emptyMap())),
      edges = listOf(GraphEdge(from = "a", fromPort = "out", to = "b", toPort = "in")),
      mode = ValidationMode.RUN
    )

    assertTrue(conditionValidator.checkedTexts.isEmpty())
    assertEquals(
      listOf("Node 'a': input port 'in' has no incoming edge. The node is skipped at run time, so nodes that depend on it do not run."),
      result.warnings.map { warning -> warning.message }
    )
  }
}
