package org.projectcontinuum.core.commons.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DefaultFlowVariablesTest {

  @Test
  fun `the prefix is the camelCase node type plus the instance id`() {
    val cases = mapOf(
      ("org.projectcontinuum.feature.base.analytics.node.RestNodeModel" to "3") to "restNode_3",
      ("org.example.KotlinScriptNodeModel" to "12") to "kotlinScriptNode_12",
      ("org.example.KNIMEWorkflowExecutorNodeModel" to "1") to "knimeWorkflowExecutorNode_1",
      ("org.example.RDKit2SVGNodeModel" to "7") to "rdKit2SVGNode_7",
      ("org.example.SVG2" to "a") to "svg2_a",
      ("org.example.Outer\$SplitNodeModel" to "4") to "splitNode_4",
      ("org.example.Model" to "x") to "model_x",
      ("org.example.TimeTrigger" to "node-2.b") to "timeTrigger_node_2_b"
    )

    cases.forEach { (input, expected) ->
      assertEquals(expected, DefaultFlowVariables.prefix(nodeModel = input.first, nodeId = input.second), input.toString())
    }
  }

  @Test
  fun `a successful run has status, timing and counts but no error`() {
    val variables = DefaultFlowVariables.build(
      prefix = "restNode_3",
      failed = false,
      errorMessage = null,
      startTimestamp = 1_000L,
      endTimestamp = 1_250L,
      inputRowCounts = mapOf("input" to 10L),
      outputRowCounts = mapOf("output" to 8L)
    )

    assertEquals(
      mapOf(
        "restNode_3.executionStatus" to "SUCCESS",
        "restNode_3.startTimestamp" to 1_000L,
        "restNode_3.endTimestamp" to 1_250L,
        "restNode_3.durationMs" to 250L,
        "restNode_3.inputCount" to mapOf("input" to 10L),
        "restNode_3.outputCount" to mapOf("output" to 8L)
      ),
      variables
    )
  }

  @Test
  fun `a failed run carries the error message and leaves unknown counts out`() {
    val variables = DefaultFlowVariables.build(
      prefix = "splitNode_5",
      failed = true,
      errorMessage = "boom",
      startTimestamp = 5L,
      endTimestamp = 9L,
      inputRowCounts = null,
      outputRowCounts = null
    )

    assertEquals("FAILED", variables["splitNode_5.executionStatus"])
    assertEquals("boom", variables["splitNode_5.errorMessage"])
    assertEquals(4L, variables["splitNode_5.durationMs"])
    assertFalse(variables.containsKey("splitNode_5.inputCount"))
    assertFalse(variables.containsKey("splitNode_5.outputCount"))
  }
}
