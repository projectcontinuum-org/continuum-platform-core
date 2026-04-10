package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkbenchStatusTest {

  @Test
  fun `all expected statuses exist`() {
    val statuses = WorkbenchStatus.entries.map { it.name }
    assertTrue(statuses.contains("PENDING"))
    assertTrue(statuses.contains("RUNNING"))
    assertTrue(statuses.contains("FAILED"))
    assertTrue(statuses.contains("SUSPENDED"))
    assertTrue(statuses.contains("UNKNOWN"))
    assertTrue(statuses.contains("TERMINATING"))
    assertTrue(statuses.contains("DELETED"))
  }

  @Test
  fun `enum has exactly 6 values`() {
    assertEquals(7, WorkbenchStatus.entries.size)
  }

  @Test
  fun `valueOf returns correct enum for valid names`() {
    assertEquals(WorkbenchStatus.PENDING, WorkbenchStatus.valueOf("PENDING"))
    assertEquals(WorkbenchStatus.RUNNING, WorkbenchStatus.valueOf("RUNNING"))
    assertEquals(WorkbenchStatus.FAILED, WorkbenchStatus.valueOf("FAILED"))
    assertEquals(WorkbenchStatus.SUSPENDED, WorkbenchStatus.valueOf("SUSPENDED"))
    assertEquals(WorkbenchStatus.UNKNOWN, WorkbenchStatus.valueOf("UNKNOWN"))
    assertEquals(WorkbenchStatus.TERMINATING, WorkbenchStatus.valueOf("TERMINATING"))
    assertEquals(WorkbenchStatus.DELETED, WorkbenchStatus.valueOf("DELETED"))
  }

  @Test
  fun `valueOf throws for invalid name`() {
    assertThrows(IllegalArgumentException::class.java) {
      WorkbenchStatus.valueOf("INVALID")
    }
  }
}
