package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class WorkbenchResponseTest {

  @Test
  fun `response holds all provided values`() {
    val instanceId = UUID.randomUUID()
    val now = Instant.now()

    val response = WorkbenchResponse(
      instanceId = instanceId,
      instanceName = "test-wb",
      namespace = "staging",
      userId = "user-1",
      status = WorkbenchStatus.RUNNING.name,
      image = "theiaide/theia:latest",
      resources = ResourceSpec(cpuRequest = "2"),
      serviceEndpoint = "wb-$instanceId-svc.staging.svc.cluster.local:8080",
      createdAt = now,
      updatedAt = now
    )

    assertEquals(instanceId, response.instanceId)
    assertEquals("test-wb", response.instanceName)
    assertEquals("staging", response.namespace)
    assertEquals("user-1", response.userId)
    assertEquals("RUNNING", response.status)
    assertEquals("theiaide/theia:latest", response.image)
    assertEquals("2", response.resources.cpuRequest)
    assertEquals("wb-$instanceId-svc.staging.svc.cluster.local:8080", response.serviceEndpoint)
    assertEquals(now, response.createdAt)
    assertEquals(now, response.updatedAt)
  }

  @Test
  fun `response allows null service endpoint`() {
    val response = WorkbenchResponse(
      instanceId = UUID.randomUUID(),
      instanceName = "test-wb",
      namespace = "default",
      userId = "user-1",
      status = "FAILED",
      image = "theiaide/theia:latest",
      resources = ResourceSpec(),
      serviceEndpoint = null,
      createdAt = Instant.now(),
      updatedAt = Instant.now()
    )

    assertNull(response.serviceEndpoint)
  }

  @Test
  fun `response equality works`() {
    val id = UUID.randomUUID()
    val now = Instant.now()

    val r1 = WorkbenchResponse(id, "wb", "ns", "u", "RUNNING", "img", ResourceSpec(), "ep", now, now)
    val r2 = WorkbenchResponse(id, "wb", "ns", "u", "RUNNING", "img", ResourceSpec(), "ep", now, now)

    assertEquals(r1, r2)
    assertEquals(r1.hashCode(), r2.hashCode())
  }
}
