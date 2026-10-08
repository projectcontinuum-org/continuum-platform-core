package org.projectcontinuum.core.cluster.manager.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.cluster.manager.model.FeatureWorkerStatus
import java.time.Instant
import java.util.UUID

class FeatureWorkerInstanceEntityTest {

  @Test
  fun `entity has correct defaults for resource and autoscaling fields`() {
    val entity = FeatureWorkerInstanceEntity(
      workerId = UUID.randomUUID(),
      workerName = "test-fw",
      namespace = "default",
      createdBy = "user-1",
      status = FeatureWorkerStatus.PENDING.name,
      image = "projectcontinuum/feature-worker:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE"
    )

    assertEquals(1, entity.replicas)
    assertFalse(entity.autoscalingEnabled)
    assertNull(entity.autoscalingMinReplicas)
    assertNull(entity.autoscalingMaxReplicas)
    assertNull(entity.autoscalingTargetCpuPercent)
    assertEquals("500m", entity.cpuRequest)
    assertEquals("2", entity.cpuLimit)
    assertEquals("512Mi", entity.memoryRequest)
    assertEquals("1Gi", entity.memoryLimit)
    assertFalse(entity.pvcEnabled)
    assertNull(entity.storageSize)
    assertNull(entity.storageClassName)
    assertEquals("{}", entity.envVars)
    assertNull(entity.overlayVariant)
    assertEquals("[]", entity.k8sResources)
    assertNull(entity.entityVersion)
  }

  @Test
  fun `entity copy preserves all fields when none overridden`() {
    val original = FeatureWorkerInstanceEntity(
      workerId = UUID.randomUUID(),
      workerName = "test-fw",
      namespace = "staging",
      createdBy = "user-1",
      status = FeatureWorkerStatus.RUNNING.name,
      image = "projectcontinuum/feature-worker:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      replicas = 3,
      autoscalingEnabled = true,
      autoscalingMinReplicas = 1,
      autoscalingMaxReplicas = 5,
      autoscalingTargetCpuPercent = 70,
      cpuRequest = "1",
      cpuLimit = "4",
      memoryRequest = "2Gi",
      memoryLimit = "8Gi",
      pvcEnabled = true,
      storageSize = "20Gi",
      storageClassName = "fast-ssd",
      envVars = """{"FOO":"bar"}""",
      overlayVariant = "gpu",
      k8sResources = "[\"deployment/test\"]",
      createdAt = Instant.parse("2026-01-01T00:00:00Z"),
      updatedAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    val copy = original.copy()

    assertEquals(original, copy)
    assertEquals(original.workerId, copy.workerId)
    assertEquals(original.storageClassName, copy.storageClassName)
    assertEquals(original.k8sResources, copy.k8sResources)
  }

  @Test
  fun `entity copy with status change only changes status`() {
    val original = FeatureWorkerInstanceEntity(
      workerId = UUID.randomUUID(),
      workerName = "test-fw",
      namespace = "default",
      createdBy = "user-1",
      status = FeatureWorkerStatus.RUNNING.name,
      image = "projectcontinuum/feature-worker:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE"
    )

    val updated = original.copy(status = FeatureWorkerStatus.DELETED.name)

    assertEquals(FeatureWorkerStatus.DELETED.name, updated.status)
    assertEquals(original.workerId, updated.workerId)
    assertEquals(original.workerName, updated.workerName)
    assertEquals(original.image, updated.image)
  }

  @Test
  fun `entity with custom task queue`() {
    val entity = FeatureWorkerInstanceEntity(
      workerId = UUID.randomUUID(),
      workerName = "test-fw",
      namespace = "default",
      createdBy = "user-1",
      status = FeatureWorkerStatus.PENDING.name,
      image = "projectcontinuum/feature-worker:latest",
      taskQueue = "CONTINUUM-FEATURE-CHEMINFORMATICS-TASK-QUEUE"
    )

    assertEquals("CONTINUUM-FEATURE-CHEMINFORMATICS-TASK-QUEUE", entity.taskQueue)
  }

  @Test
  fun `entity equality is based on all fields`() {
    val id = UUID.randomUUID()
    val now = Instant.now()

    val entity1 = FeatureWorkerInstanceEntity(
      workerId = id,
      workerName = "test",
      namespace = "default",
      createdBy = "user-1",
      status = "RUNNING",
      image = "image:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      createdAt = now,
      updatedAt = now
    )

    val entity2 = FeatureWorkerInstanceEntity(
      workerId = id,
      workerName = "test",
      namespace = "default",
      createdBy = "user-1",
      status = "RUNNING",
      image = "image:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      createdAt = now,
      updatedAt = now
    )

    assertEquals(entity1, entity2)
    assertEquals(entity1.hashCode(), entity2.hashCode())
  }

  @Test
  fun `entities with different ids are not equal`() {
    val now = Instant.now()

    val entity1 = FeatureWorkerInstanceEntity(
      workerId = UUID.randomUUID(),
      workerName = "test",
      namespace = "default",
      createdBy = "user-1",
      status = "RUNNING",
      image = "image:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      createdAt = now,
      updatedAt = now
    )

    val entity2 = FeatureWorkerInstanceEntity(
      workerId = UUID.randomUUID(),
      workerName = "test",
      namespace = "default",
      createdBy = "user-1",
      status = "RUNNING",
      image = "image:latest",
      taskQueue = "CONTINUUM-FEATURE-TEST-TASK-QUEUE",
      createdAt = now,
      updatedAt = now
    )

    assertNotEquals(entity1, entity2)
  }
}
