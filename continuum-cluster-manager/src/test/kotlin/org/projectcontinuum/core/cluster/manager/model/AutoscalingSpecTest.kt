package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AutoscalingSpecTest {

  @Test
  fun `default values are correct`() {
    val spec = AutoscalingSpec()

    assertFalse(spec.enabled)
    assertNull(spec.minReplicas)
    assertNull(spec.maxReplicas)
    assertNull(spec.targetCPUUtilizationPercentage)
  }

  @Test
  fun `custom values are preserved`() {
    val spec = AutoscalingSpec(
      enabled = true,
      minReplicas = 2,
      maxReplicas = 10,
      targetCPUUtilizationPercentage = 75
    )

    assertTrue(spec.enabled)
    assertEquals(2, spec.minReplicas)
    assertEquals(10, spec.maxReplicas)
    assertEquals(75, spec.targetCPUUtilizationPercentage)
  }

  @Test
  fun `copy with partial overrides`() {
    val original = AutoscalingSpec(enabled = true, minReplicas = 1, maxReplicas = 5, targetCPUUtilizationPercentage = 80)
    val modified = original.copy(maxReplicas = 8)

    assertEquals(8, modified.maxReplicas)
    assertEquals(original.minReplicas, modified.minReplicas)
    assertEquals(original.enabled, modified.enabled)
  }

  @Test
  fun `equality works correctly`() {
    val spec1 = AutoscalingSpec(enabled = true, minReplicas = 1, maxReplicas = 5, targetCPUUtilizationPercentage = 70)
    val spec2 = AutoscalingSpec(enabled = true, minReplicas = 1, maxReplicas = 5, targetCPUUtilizationPercentage = 70)

    assertEquals(spec1, spec2)
    assertEquals(spec1.hashCode(), spec2.hashCode())
  }

  @Test
  fun `inequality when different values`() {
    val spec1 = AutoscalingSpec(enabled = true)
    val spec2 = AutoscalingSpec(enabled = false)

    assertNotEquals(spec1, spec2)
  }
}
