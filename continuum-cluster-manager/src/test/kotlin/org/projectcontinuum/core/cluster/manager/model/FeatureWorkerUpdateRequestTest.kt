package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeatureWorkerUpdateRequestTest {

  @Test
  fun `only taskQueue is required, other fields default to null`() {
    val request = FeatureWorkerUpdateRequest(taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE")

    assertEquals("CONTINUUM-FEATURE-TEST-TASK-QUEUE", request.taskQueue)
    assertNull(request.image)
    assertNull(request.resources)
    assertNull(request.replicas)
    assertNull(request.autoscaling)
    assertNull(request.pvcEnabled)
    assertNull(request.envVars)
  }

  @Test
  fun `image only update`() {
    val request = FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      image = "projectcontinuum/feature-worker:v2"
    )

    assertEquals("projectcontinuum/feature-worker:v2", request.image)
    assertNull(request.resources)
  }

  @Test
  fun `resources only update`() {
    val resources = FeatureWorkerResourceSpec(cpuRequest = "4")
    val request = FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      resources = resources
    )

    assertNull(request.image)
    assertEquals("4", request.resources!!.cpuRequest)
  }

  @Test
  fun `autoscaling and pvc toggles update`() {
    val request = FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      autoscaling = AutoscalingSpec(enabled = true, minReplicas = 2, maxReplicas = 6),
      pvcEnabled = true
    )

    assertTrue(request.autoscaling!!.enabled)
    assertEquals(2, request.autoscaling!!.minReplicas)
    assertTrue(request.pvcEnabled!!)
  }

  @Test
  fun `envVars update`() {
    val request = FeatureWorkerUpdateRequest(
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      envVars = mapOf("FOO" to "bar")
    )

    assertEquals("bar", request.envVars!!["FOO"])
  }
}
