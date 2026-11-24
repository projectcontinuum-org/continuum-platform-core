package org.projectcontinuum.core.commons.context

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExecutionContextTest {

  data class Threshold(val column: String, val limit: Int)

  @Test
  fun `inherited flow variables are readable, raw and converted`() {
    val executionContext = ExecutionContext(
      inputFlowVariables = mapOf(
        "count" to 3,
        "label" to "batch",
        "threshold" to mapOf("column" to "score", "limit" to 10)
      )
    )

    assertEquals(3, executionContext.getFlowVariable("count"))
    assertEquals(3L, executionContext.getFlowVariable<Long>("count"))
    assertEquals("batch", executionContext.getFlowVariable<String>("label"))
    assertEquals(Threshold(column = "score", limit = 10), executionContext.getFlowVariable<Threshold>("threshold"))
    assertNull(executionContext.getFlowVariable("missing"))
    assertNull(executionContext.getFlowVariable<Int>("missing"))
  }

  @Test
  fun `a value that cannot be converted fails`() {
    val executionContext = ExecutionContext(inputFlowVariables = mapOf("label" to "batch"))

    assertThrows<IllegalArgumentException> { executionContext.getFlowVariable<Int>("label") }
  }

  @Test
  fun `writes collect into the output map and never change the inherited variables`() {
    val executionContext = ExecutionContext(inputFlowVariables = mapOf("x" to 1))

    executionContext.setFlowVariable(name = "x", value = 10)
    executionContext.setFlowVariables(variables = mapOf("y" to 2, "z" to 3))
    executionContext.setFlowVariable(name = "y", value = 20)

    assertEquals(mapOf("x" to 1), executionContext.inputFlowVariables)
    assertEquals(1, executionContext.getFlowVariable("x"))
    assertEquals(mapOf("x" to 10, "y" to 20, "z" to 3), executionContext.getOutputFlowVariables())
  }

  @Test
  fun `getOutputFlowVariables returns a copy`() {
    val executionContext = ExecutionContext()
    executionContext.setFlowVariable(name = "x", value = 1)

    val snapshot = executionContext.getOutputFlowVariables()
    executionContext.setFlowVariable(name = "y", value = 2)

    assertEquals(mapOf("x" to 1), snapshot)
  }

  @Test
  fun `a blank name is rejected`() {
    val executionContext = ExecutionContext()

    assertThrows<IllegalArgumentException> { executionContext.setFlowVariable(name = " ", value = 1) }
    assertTrue(executionContext.getOutputFlowVariables().isEmpty())
  }

  @Test
  fun `EMPTY is a fresh context every time so flow variables never leak between nodes`() {
    val first = ExecutionContext.EMPTY
    first.setFlowVariable(name = "leak", value = true)

    val second = ExecutionContext.EMPTY

    assertNotSame(first, second)
    assertTrue(second.getOutputFlowVariables().isEmpty())
    assertTrue(second.inputFlowVariables.isEmpty())
  }
}
