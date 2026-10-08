package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class FeatureWorkerResponseTest {

  @Test
  fun `response holds all provided values`() {
    val workerId = UUID.randomUUID()
    val now = Instant.now()

    val response = FeatureWorkerResponse(
      workerId = workerId,
      workerName = "test-fw",
      namespace = "staging",
      createdBy = "user-1",
      status = FeatureWorkerStatus.RUNNING.name,
      image = "projectcontinuum/feature-worker:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      replicas = 2,
      autoscaling = AutoscalingSpec(enabled = true, minReplicas = 1, maxReplicas = 5, targetCPUUtilizationPercentage = 70),
      resources = FeatureWorkerResourceSpec(cpuRequest = "2"),
      pvcEnabled = true,
      envVars = mapOf("FOO" to "bar"),
      overlayVariant = "gpu",
      createdAt = now,
      updatedAt = now
    )

    assertEquals(workerId, response.workerId)
    assertEquals("test-fw", response.workerName)
    assertEquals("staging", response.namespace)
    assertEquals("user-1", response.createdBy)
    assertEquals("RUNNING", response.status)
    assertEquals("CONTINUUM-FEATURE-TEST-TASK-QUEUE", response.taskQueue)
    assertEquals(2, response.replicas)
    assertTrue(response.autoscaling.enabled)
    assertEquals("2", response.resources.cpuRequest)
    assertTrue(response.pvcEnabled)
    assertEquals("bar", response.envVars["FOO"])
    assertEquals("gpu", response.overlayVariant)
    assertEquals(now, response.createdAt)
    assertEquals(now, response.updatedAt)
  }

  @Test
  fun `response allows null overlayVariant`() {
    val response = FeatureWorkerResponse(
      workerId = UUID.randomUUID(),
      workerName = "test-fw",
      namespace = "default",
      createdBy = "user-1",
      status = "FAILED",
      image = "projectcontinuum/feature-worker:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      replicas = 1,
      autoscaling = AutoscalingSpec(),
      resources = FeatureWorkerResourceSpec(),
      pvcEnabled = false,
      envVars = emptyMap(),
      overlayVariant = null,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )

    assertNull(response.overlayVariant)
  }

  @Test
  fun `response equality works`() {
    val id = UUID.randomUUID()
    val now = Instant.now()

    val r1 = FeatureWorkerResponse(id, "fw", "ns", "u", "RUNNING", "img", "CONTINUUM-FEATURE-FW-TASK-QUEUE", 1, AutoscalingSpec(), FeatureWorkerResourceSpec(), false, emptyMap(), null, now, now)
    val r2 = FeatureWorkerResponse(id, "fw", "ns", "u", "RUNNING", "img", "CONTINUUM-FEATURE-FW-TASK-QUEUE", 1, AutoscalingSpec(), FeatureWorkerResourceSpec(), false, emptyMap(), null, now, now)

    assertEquals(r1, r2)
    assertEquals(r1.hashCode(), r2.hashCode())
  }
}
