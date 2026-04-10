package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkbenchCreateRequestTest {

  @Test
  fun `default values are correct`() {
    val request = WorkbenchCreateRequest(instanceName = "test-wb")

    assertEquals("test-wb", request.instanceName)
    assertEquals("projectcontinuum/continuum-workbench:0.0.5", request.image)
    assertEquals(ResourceSpec(), request.resources)
  }

  @Test
  fun `custom values override defaults`() {
    val resources = ResourceSpec(cpuRequest = "4")
    val request = WorkbenchCreateRequest(
      instanceName = "custom-wb",
      resources = resources,
      image = "theiaide/theia:v2"
    )

    assertEquals("custom-wb", request.instanceName)
    assertEquals("theiaide/theia:v2", request.image)
    assertEquals("4", request.resources.cpuRequest)
  }

  @Test
  fun `equality based on all fields`() {
    val req1 = WorkbenchCreateRequest(instanceName = "wb")
    val req2 = WorkbenchCreateRequest(instanceName = "wb")

    assertEquals(req1, req2)
  }

  @Test
  fun `inequality when different instance names`() {
    val req1 = WorkbenchCreateRequest(instanceName = "wb-1")
    val req2 = WorkbenchCreateRequest(instanceName = "wb-2")

    assertNotEquals(req1, req2)
  }
}
