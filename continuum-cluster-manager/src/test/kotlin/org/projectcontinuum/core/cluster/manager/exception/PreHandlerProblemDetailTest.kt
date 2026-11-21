package org.projectcontinuum.core.cluster.manager.exception

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Errors raised before a controller method is chosen (unknown path, 405, 406, 415) are `application/problem+json`
 * with the legacy `error` property too. [GlobalExceptionHandler] must not be scoped by package for that: Spring only
 * applies a scoped advice when a handler method was resolved. None of these requests reaches Kubernetes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreHandlerProblemDetailTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Test
  fun `an unsupported method is a 405 problem with the legacy error text`() {
    mockMvc.perform(patch("/api/v1/workbench/analysis").header("x-continuum-user-id", "alice"))
      .andExpect(status().isMethodNotAllowed)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(405))
      .andExpect(jsonPath("$.error").exists())
  }

  @Test
  fun `an unsupported content type is a 415 problem`() {
    mockMvc.perform(
      post("/api/v1/workbench")
        .header("x-continuum-user-id", "alice")
        .contentType(MediaType.TEXT_PLAIN)
        .content("instanceName=analysis")
    )
      .andExpect(status().isUnsupportedMediaType)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.error").exists())
  }

  @Test
  fun `an Accept the operation cannot produce is a 406 problem`() {
    mockMvc.perform(get("/api/v1/workbench/variants").accept(MediaType.TEXT_PLAIN))
      .andExpect(status().isNotAcceptable)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(406))
  }

  @Test
  fun `an unknown API path is a 404 problem`() {
    mockMvc.perform(get("/api/v1/no-such-endpoint"))
      .andExpect(status().isNotFound)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(404))
      .andExpect(jsonPath("$.error").exists())
  }
}
