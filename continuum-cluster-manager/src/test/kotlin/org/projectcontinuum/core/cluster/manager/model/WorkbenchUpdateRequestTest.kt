package org.projectcontinuum.core.cluster.manager.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkbenchUpdateRequestTest {

  @Test
  fun `default values are null`() {
    val request = WorkbenchUpdateRequest()

    assertNull(request.resources)
    assertNull(request.image)
  }

  @Test
  fun `image only update`() {
    val request = WorkbenchUpdateRequest(image = "theiaide/theia:v2")

    assertEquals("theiaide/theia:v2", request.image)
    assertNull(request.resources)
  }

  @Test
  fun `resources only update`() {
    val resources = ResourceSpec(cpuRequest = "4")
    val request = WorkbenchUpdateRequest(resources = resources)

    assertNull(request.image)
    assertEquals("4", request.resources!!.cpuRequest)
  }

  @Test
  fun `both image and resources update`() {
    val request = WorkbenchUpdateRequest(
      image = "theiaide/theia:v3",
      resources = ResourceSpec(memoryLimit = "16Gi")
    )

    assertEquals("theiaide/theia:v3", request.image)
    assertEquals("16Gi", request.resources!!.memoryLimit)
  }
}
