package org.projectcontinuum.core.loopcondition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.projectcontinuum.core.commons.validation.loop.LoopConditionCheckResult
import org.projectcontinuum.core.commons.validation.loop.LoopConditionIssueCode
import org.projectcontinuum.core.commons.validation.loop.LoopConditionResultType

/** Every row of the CEL condition table against the shared validator. */
class LoopConditionValidatorTableTest {

  private val validator = LoopConditionValidators.default

  @TestFactory
  fun `every table row gives its expected validation result`(): List<DynamicTest> =
    CelConditionTable.rows.map { row ->
      DynamicTest.dynamicTest(row.id) {
        val result = validator.check(text = row.input)
        if (row.valid) {
          assertTrue(result is LoopConditionCheckResult.Valid, "${row.id}: expected valid, got $result")
          val valid = result as LoopConditionCheckResult.Valid
          assertEquals(LoopConditionResultType.valueOf(row.resultType!!.uppercase()), valid.resultType, row.id)
          assertEquals(row.readsVariables, valid.readVariables, row.id)
        } else {
          assertTrue(result is LoopConditionCheckResult.Invalid, "${row.id}: expected invalid, got $result")
          val invalid = result as LoopConditionCheckResult.Invalid
          assertEquals(LoopConditionIssueCode.valueOf(row.code!!), invalid.code, "${row.id}: ${invalid.message}")
          assertTrue(invalid.message.startsWith(invalid.code.reasonText), "${row.id}: ${invalid.message}")
          row.column?.let { expectedColumn -> assertEquals(expectedColumn, invalid.column, "${row.id}: ${invalid.message}") }
        }
      }
    }

  @Test
  fun `the table has a row for every validation code and every code is a known one`() {
    val validationCodes = LoopConditionIssueCode.entries.filter { code -> !code.isRuntime }.map { code -> code.name }.toSet()
    val tableCodes = CelConditionTable.rows.mapNotNull { row -> row.code }.toSet()

    assertEquals(validationCodes, tableCodes)
  }

  @Test
  fun `the table covers both result types, evaluations and every runtime code`() {
    val rows = CelConditionTable.rows
    assertTrue(rows.count { row -> row.valid } >= 40)
    assertTrue(rows.any { row -> row.resultType == "dyn" })
    val expectedOutcomes = rows.flatMap { row -> row.evaluations.map { evaluation -> evaluation.expect } }.toSet()
    val runtimeCodes = LoopConditionIssueCode.entries.filter { code -> code.isRuntime && code != LoopConditionIssueCode.NON_BOOLEAN_RESULT }
    runtimeCodes.forEach { code -> assertTrue(code.name in expectedOutcomes, "no evaluation expects ${code.name}") }
    assertTrue(setOf("true", "false", "non-boolean").all { outcome -> outcome in expectedOutcomes })
  }
}
