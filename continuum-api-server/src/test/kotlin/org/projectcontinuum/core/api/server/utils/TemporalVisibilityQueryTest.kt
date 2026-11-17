package org.projectcontinuum.core.api.server.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.projectcontinuum.core.api.server.exception.InvalidRequestException
import kotlin.test.assertEquals

class TemporalVisibilityQueryTest {

  @Test
  fun `quote escapes single quotes and backslashes`() {
    assertEquals("'abc'", TemporalVisibilityQuery.quote("abc"))
    assertEquals("'x\\' OR WorkflowId != \\''", TemporalVisibilityQuery.quote("x' OR WorkflowId != '"))
    assertEquals("'a\\\\\\' OR 1=1 --'", TemporalVisibilityQuery.quote("a\\' OR 1=1 --"))
  }

  @Test
  fun `workflowIdIn quotes every id`() {
    assertEquals(
      "WorkflowId IN ('a', 'b\\')')",
      TemporalVisibilityQuery.workflowIdIn(listOf("a", "b')"))
    )
  }

  @Test
  fun `scope with blank client query returns only the server filter`() {
    assertEquals("(WorkflowId IN ('a'))", TemporalVisibilityQuery.scope("WorkflowId IN ('a')", "  "))
    assertEquals("(WorkflowId IN ('a'))", TemporalVisibilityQuery.scope("WorkflowId IN ('a')", null))
  }

  @Test
  fun `scope AND-combines a legitimate client query in parentheses`() {
    assertEquals(
      "(WorkflowId IN ('a')) AND (ExecutionStatus = 'Running' OR `Continuum:WorkflowFileName` = 'it''s (1).cwf')",
      TemporalVisibilityQuery.scope(
        "WorkflowId IN ('a')",
        "ExecutionStatus = 'Running' OR `Continuum:WorkflowFileName` = 'it''s (1).cwf'"
      )
    )
  }

  @Test
  fun `scope keeps a trailing GROUP BY ExecutionStatus outside the parentheses for counts`() {
    assertEquals(
      "(WorkflowId IN ('a')) AND (WorkflowType = 'IContinuumWorkflow') GROUP BY ExecutionStatus",
      TemporalVisibilityQuery.scope(
        "WorkflowId IN ('a')",
        "WorkflowType = 'IContinuumWorkflow' group by ExecutionStatus",
        allowGroupByExecutionStatus = true
      )
    )
    assertEquals(
      "(WorkflowId IN ('a')) GROUP BY ExecutionStatus",
      TemporalVisibilityQuery.scope("WorkflowId IN ('a')", "GROUP BY ExecutionStatus", allowGroupByExecutionStatus = true)
    )
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "'",
      "'); SELECT 1; --",
      "x') OR (1=1",
      ") OR (WorkflowId != ''",
      "WorkflowType = 'a' OR 1=1 --",
      "WorkflowType = 'a' /* c */",
      "WorkflowType = 'a' # c",
      "WorkflowType = 'a'; DROP",
      "WorkflowType = 'a\\' OR 1=1",
      "WorkflowType = 'a'\nOR 1=1",
      "WorkflowType = \"unterminated",
      "((WorkflowType = 'a')",
      "\$(id)) OR ((1=1",
    ]
  )
  fun `rejects client queries that could escape their parentheses`(hostile: String) {
    assertThrows<InvalidRequestException> { TemporalVisibilityQuery.scope("WorkflowId IN ('a')", hostile) }
  }

  @Test
  fun `parentheses and comment markers inside literals are literal`() {
    assertEquals(
      "(S) AND (WorkflowId = ') OR (1=1 -- ; # /*')",
      TemporalVisibilityQuery.scope("S", "WorkflowId = ') OR (1=1 -- ; # /*'")
    )
  }

  @Test
  fun `an escape attempt in front of an allowed GROUP BY is still rejected`() {
    assertThrows<InvalidRequestException> {
      TemporalVisibilityQuery.scope("S", "x) OR (1=1) GROUP BY ExecutionStatus", allowGroupByExecutionStatus = true)
    }
  }
}
