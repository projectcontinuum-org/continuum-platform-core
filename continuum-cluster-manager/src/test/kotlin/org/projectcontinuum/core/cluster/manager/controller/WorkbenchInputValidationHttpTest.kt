package org.projectcontinuum.core.cluster.manager.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.cluster.manager.repository.WorkbenchInstanceRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * End-to-end (HTTP → real WorkbenchService) check that hostile template values are
 * rejected with 400 at the service boundary. Validation runs before any Kubernetes call,
 * so the (unreachable) real client in this context is never contacted.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbenchInputValidationHttpTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Autowired
  private lateinit var repository: WorkbenchInstanceRepository

  @BeforeEach
  fun setUp() {
    repository.deleteAll()
  }

  /**
   * Full resources object: a partial one currently fails Jackson deserialization (500) before
   * reaching the service — a separate, pre-existing issue unrelated to these checks.
   */
  private fun resources(
    cpuRequest: String = "500m",
    memoryLimit: String = "1Gi",
    storageSize: String = "5Gi",
    storageClassName: String? = null
  ): String = jacksonObjectMapper().writeValueAsString(
    mapOf(
      "cpuRequest" to cpuRequest, "cpuLimit" to "2", "memoryRequest" to "512Mi",
      "memoryLimit" to memoryLimit, "storageSize" to storageSize, "storageClassName" to storageClassName
    )
  )

  private fun postCreate(body: String) =
    mockMvc.perform(
      post("/api/v1/workbench")
        .header("x-continuum-user-id", "user-1")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
    )

  @Test
  fun `POST with Secret injected into memoryLimit returns 400`() {
    postCreate(
      """{"instanceName":"wb","resources":${resources(memoryLimit = "1Gi\n---\napiVersion: v1\nkind: Secret\nmetadata:\n  name: x")}}"""
    )
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("memoryLimit")))
    assertTrue(repository.findAll().none())
  }

  @Test
  fun `POST with YAML field injected into storageSize returns 400`() {
    postCreate("""{"instanceName":"wb","resources":${resources(storageSize = "1Gi\n  x: y")}}""")
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `POST with path-like storageClassName returns 400`() {
    postCreate("""{"instanceName":"wb","resources":${resources(storageClassName = "../../etc")}}""")
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `POST with image containing spaces returns 400`() {
    postCreate("""{"instanceName":"wb","image":"projectcontinuum/continuum-workbench:1 evil"}""")
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `POST with image outside allowed prefixes returns 400`() {
    postCreate("""{"instanceName":"wb","image":"evil.example.com/miner:latest"}""")
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("not allowed")))
  }

  @Test
  fun `POST with path-traversal variant returns 400`() {
    postCreate("""{"instanceName":"wb","variant":"../../etc/passwd"}""")
      .andExpect(status().isBadRequest)
    assertTrue(repository.findAll().none())
  }
}
