package org.projectcontinuum.core.cluster.manager.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.cluster.manager.model.WorkbenchStatus
import java.time.Instant
import java.util.UUID

class WorkbenchInstanceEntityTest {

  @Test
  fun `entity has correct defaults for resource fields`() {
    val entity = WorkbenchInstanceEntity(
      instanceId = UUID.randomUUID(),
      instanceName = "test-wb",
      namespace = "default",
      userId = "user-1",
      status = WorkbenchStatus.PENDING.name,
      image = "theiaide/theia:latest"
    )

    assertEquals("500m", entity.cpuRequest)
    assertEquals("2", entity.cpuLimit)
    assertEquals("512Mi", entity.memoryRequest)
    assertEquals("1Gi", entity.memoryLimit)
    assertEquals("5Gi", entity.storageSize)
    assertNull(entity.storageClassName)
    assertEquals("[]", entity.k8sResources)
    assertNull(entity.entityVersion)
  }

  @Test
  fun `entity copy preserves all fields when none overridden`() {
    val original = WorkbenchInstanceEntity(
      instanceId = UUID.randomUUID(),
      instanceName = "test-wb",
      namespace = "staging",
      userId = "user-1",
      status = WorkbenchStatus.RUNNING.name,
      image = "theiaide/theia:latest",
      cpuRequest = "1",
      cpuLimit = "4",
      memoryRequest = "2Gi",
      memoryLimit = "8Gi",
      storageSize = "20Gi",
      storageClassName = "fast-ssd",
      k8sResources = "[\"deployment/test\"]",
      createdAt = Instant.parse("2026-01-01T00:00:00Z"),
      updatedAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    val copy = original.copy()

    assertEquals(original, copy)
    assertEquals(original.instanceId, copy.instanceId)
    assertEquals(original.storageClassName, copy.storageClassName)
    assertEquals(original.k8sResources, copy.k8sResources)
  }

  @Test
  fun `entity copy with status change only changes status`() {
    val original = WorkbenchInstanceEntity(
      instanceId = UUID.randomUUID(),
      instanceName = "test-wb",
      namespace = "default",
      userId = "user-1",
      status = WorkbenchStatus.RUNNING.name,
      image = "theiaide/theia:latest"
    )

    val updated = original.copy(status = WorkbenchStatus.DELETED.name)

    assertEquals(WorkbenchStatus.DELETED.name, updated.status)
    assertEquals(original.instanceId, updated.instanceId)
    assertEquals(original.instanceName, updated.instanceName)
    assertEquals(original.image, updated.image)
  }

  @Test
  fun `entity with custom storage class name`() {
    val entity = WorkbenchInstanceEntity(
      instanceId = UUID.randomUUID(),
      instanceName = "test-wb",
      namespace = "default",
      userId = "user-1",
      status = WorkbenchStatus.PENDING.name,
      image = "theiaide/theia:latest",
      storageClassName = "gp3"
    )

    assertEquals("gp3", entity.storageClassName)
  }

  @Test
  fun `entity equality is based on all fields`() {
    val id = UUID.randomUUID()
    val now = Instant.now()

    val entity1 = WorkbenchInstanceEntity(
      instanceId = id,
      instanceName = "test",
      namespace = "default",
      userId = "user-1",
      status = "RUNNING",
      image = "image:latest",
      createdAt = now,
      updatedAt = now
    )

    val entity2 = WorkbenchInstanceEntity(
      instanceId = id,
      instanceName = "test",
      namespace = "default",
      userId = "user-1",
      status = "RUNNING",
      image = "image:latest",
      createdAt = now,
      updatedAt = now
    )

    assertEquals(entity1, entity2)
    assertEquals(entity1.hashCode(), entity2.hashCode())
  }

  @Test
  fun `entities with different ids are not equal`() {
    val now = Instant.now()

    val entity1 = WorkbenchInstanceEntity(
      instanceId = UUID.randomUUID(),
      instanceName = "test",
      namespace = "default",
      userId = "user-1",
      status = "RUNNING",
      image = "image:latest",
      createdAt = now,
      updatedAt = now
    )

    val entity2 = WorkbenchInstanceEntity(
      instanceId = UUID.randomUUID(),
      instanceName = "test",
      namespace = "default",
      userId = "user-1",
      status = "RUNNING",
      image = "image:latest",
      createdAt = now,
      updatedAt = now
    )

    assertNotEquals(entity1, entity2)
  }
}
