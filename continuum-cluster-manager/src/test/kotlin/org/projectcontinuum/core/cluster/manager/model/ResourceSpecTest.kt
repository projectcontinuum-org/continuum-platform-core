package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResourceSpecTest {

  @Test
  fun `default values are correct`() {
    val spec = ResourceSpec()

    assertEquals("500m", spec.cpuRequest)
    assertEquals("2", spec.cpuLimit)
    assertEquals("512Mi", spec.memoryRequest)
    assertEquals("1Gi", spec.memoryLimit)
    assertEquals("5Gi", spec.storageSize)
    assertNull(spec.storageClassName)
  }

  @Test
  fun `custom values are preserved`() {
    val spec = ResourceSpec(
      cpuRequest = "4",
      cpuLimit = "8",
      memoryRequest = "8Gi",
      memoryLimit = "16Gi",
      storageSize = "100Gi",
      storageClassName = "premium-ssd"
    )

    assertEquals("4", spec.cpuRequest)
    assertEquals("8", spec.cpuLimit)
    assertEquals("8Gi", spec.memoryRequest)
    assertEquals("16Gi", spec.memoryLimit)
    assertEquals("100Gi", spec.storageSize)
    assertEquals("premium-ssd", spec.storageClassName)
  }

  @Test
  fun `copy with partial overrides`() {
    val original = ResourceSpec(cpuRequest = "1", storageClassName = "standard")
    val modified = original.copy(cpuRequest = "2")

    assertEquals("2", modified.cpuRequest)
    assertEquals("standard", modified.storageClassName)
    assertEquals(original.cpuLimit, modified.cpuLimit)
  }

  @Test
  fun `equality works correctly`() {
    val spec1 = ResourceSpec(cpuRequest = "1")
    val spec2 = ResourceSpec(cpuRequest = "1")

    assertEquals(spec1, spec2)
    assertEquals(spec1.hashCode(), spec2.hashCode())
  }

  @Test
  fun `inequality when different values`() {
    val spec1 = ResourceSpec(cpuRequest = "1")
    val spec2 = ResourceSpec(cpuRequest = "2")

    assertNotEquals(spec1, spec2)
  }
}
