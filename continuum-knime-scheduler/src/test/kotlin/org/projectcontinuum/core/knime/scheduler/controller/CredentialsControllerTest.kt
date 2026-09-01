package org.projectcontinuum.core.knime.scheduler.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.knime.scheduler.client.CredentialSummaryResponse
import org.projectcontinuum.core.knime.scheduler.exception.GlobalExceptionHandler
import org.projectcontinuum.core.knime.scheduler.service.CredentialsService
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class CredentialsControllerTest {

  private val service: CredentialsService = mock()
  private lateinit var mockMvc: MockMvc

  @BeforeEach
  fun setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(CredentialsController(service))
      .setControllerAdvice(GlobalExceptionHandler())
      .build()
  }

  @Test
  fun `GET returns GENERIC credentials for the calling user without a type param`() {
    whenever(service.listByType(eq("alice"), eq("GENERIC")))
      .thenReturn(listOf(CredentialSummaryResponse(name = "db-cred", type = "GENERIC")))

    mockMvc.perform(
      get("/api/v1/credentials")
        .header("x-continuum-user-id", "alice")
    )
      .andExpect(status().isOk)
      .andExpect(jsonPath("$.length()").value(1))
      .andExpect(jsonPath("$[0].name").value("db-cred"))
      .andExpect(jsonPath("$[0].type").value("GENERIC"))
  }
}
