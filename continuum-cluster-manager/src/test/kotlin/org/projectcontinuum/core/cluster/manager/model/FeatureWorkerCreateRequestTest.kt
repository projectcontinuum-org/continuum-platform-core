package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeatureWorkerCreateRequestTest {

  @Test
  fun `required fields are set and defaults resolve correctly`() {
    val request = FeatureWorkerCreateRequest(
      workerName = "test-fw",
      image = "projectcontinuum/feature-worker:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE"
    )

    assertEquals("test-fw", request.workerName)
    assertEquals("projectcontinuum/feature-worker:latest", request.image)
    assertEquals("CONTINUUM-FEATURE-TEST-TASK-QUEUE", request.taskQueue)
    assertEquals(1, request.replicas)
    assertFalse(request.pvcEnabled)
    assertEquals(emptyMap<String, String>(), request.envVars)
    assertNull(request.variant)
    assertEquals(FeatureWorkerResourceSpec(), request.resolvedResources())
    assertEquals(AutoscalingSpec(), request.resolvedAutoscaling())
  }

  @Test
  fun `custom values override defaults`() {
    val resources = FeatureWorkerResourceSpec(cpuRequest = "4")
    val autoscaling = AutoscalingSpec(enabled = true, minReplicas = 1, maxReplicas = 5)
    val request = FeatureWorkerCreateRequest(
      workerName = "custom-fw",
      image = "projectcontinuum/feature-worker:v2",
      taskQueue = "CONTINUUM-FEATURE-CUSTOM-TASK-QUEUE",
      resources = resources,
      replicas = 3,
      autoscaling = autoscaling,
      pvcEnabled = true,
      envVars = mapOf("FOO" to "bar"),
      variant = "gpu"
    )

    assertEquals("custom-fw", request.workerName)
    assertEquals(3, request.replicas)
    assertTrue(request.pvcEnabled)
    assertEquals("bar", request.envVars["FOO"])
    assertEquals("gpu", request.variant)
    assertEquals("4", request.resolvedResources().cpuRequest)
    assertTrue(request.resolvedAutoscaling().enabled)
  }

  @Test
  fun `equality based on all fields`() {
    val req1 = FeatureWorkerCreateRequest(workerName = "fw", image = "img", taskQueue = "CONTINUUM-FEATURE-FW-TASK-QUEUE")
    val req2 = FeatureWorkerCreateRequest(workerName = "fw", image = "img", taskQueue = "CONTINUUM-FEATURE-FW-TASK-QUEUE")

    assertEquals(req1, req2)
  }

  @Test
  fun `inequality when different worker names`() {
    val req1 = FeatureWorkerCreateRequest(workerName = "fw-1", image = "img", taskQueue = "CONTINUUM-FEATURE-ONE-TASK-QUEUE")
    val req2 = FeatureWorkerCreateRequest(workerName = "fw-2", image = "img", taskQueue = "CONTINUUM-FEATURE-TWO-TASK-QUEUE")

    assertNotEquals(req1, req2)
  }
}
