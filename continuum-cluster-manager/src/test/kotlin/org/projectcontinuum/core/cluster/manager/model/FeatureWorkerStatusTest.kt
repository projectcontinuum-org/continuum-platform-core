package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeatureWorkerStatusTest {

  @Test
  fun `all expected statuses exist`() {
    val statuses = FeatureWorkerStatus.entries.map { it.name }
    assertTrue(statuses.contains("PENDING"))
    assertTrue(statuses.contains("RUNNING"))
    assertTrue(statuses.contains("FAILED"))
    assertTrue(statuses.contains("UNKNOWN"))
    assertTrue(statuses.contains("TERMINATING"))
    assertTrue(statuses.contains("DELETED"))
  }

  @Test
  fun `enum has exactly 6 values and no SUSPENDED state`() {
    assertEquals(6, FeatureWorkerStatus.entries.size)
    assertFalse(FeatureWorkerStatus.entries.map { it.name }.contains("SUSPENDED"))
  }

  @Test
  fun `valueOf returns correct enum for valid names`() {
    assertEquals(FeatureWorkerStatus.PENDING, FeatureWorkerStatus.valueOf("PENDING"))
    assertEquals(FeatureWorkerStatus.RUNNING, FeatureWorkerStatus.valueOf("RUNNING"))
    assertEquals(FeatureWorkerStatus.FAILED, FeatureWorkerStatus.valueOf("FAILED"))
    assertEquals(FeatureWorkerStatus.UNKNOWN, FeatureWorkerStatus.valueOf("UNKNOWN"))
    assertEquals(FeatureWorkerStatus.TERMINATING, FeatureWorkerStatus.valueOf("TERMINATING"))
    assertEquals(FeatureWorkerStatus.DELETED, FeatureWorkerStatus.valueOf("DELETED"))
  }

  @Test
  fun `valueOf throws for invalid name`() {
    assertThrows(IllegalArgumentException::class.java) {
      FeatureWorkerStatus.valueOf("SUSPENDED")
    }
  }
}
