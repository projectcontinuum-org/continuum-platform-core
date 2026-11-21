package org.projectcontinuum.core.cluster.manager.openapi

import io.fabric8.kubernetes.client.KubernetesClient
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.cluster.manager.service.WorkbenchService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Forwarded headers through the real filter chain: honoured for redirects (`X-Forwarded-Prefix`), but passed through
 * untouched to the Traefik ForwardAuth endpoint, which reads `X-Forwarded-Host` itself.
 */
@SpringBootTest(
  properties = [
    OpenApiSpecTest.MAIN_APPLICATION_YML,
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.schema-locations=classpath:schema-h2.sql"
  ]
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForwardedHeadersTest {

  @MockitoBean
  lateinit var kubernetesClient: KubernetesClient

  @MockitoBean
  lateinit var workbenchService: WorkbenchService

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Test
  fun `authorize still receives X-Forwarded-Host and allows the owner`() {
    whenever(workbenchService.authorizeAccess(eq("alice"), eq("wb-1.continuum.example.com"))).thenReturn(true)

    mockMvc.perform(
      get("/api/v1/workbench/authorize")
        .header("x-continuum-user-id", "alice")
        .header("X-Forwarded-Host", "wb-1.continuum.example.com")
        .header("X-Forwarded-Prefix", "/cluster-manager")
        .header("X-Forwarded-Proto", "https")
    ).andExpect(status().isOk)

    verify(workbenchService).authorizeAccess(eq("alice"), eq("wb-1.continuum.example.com"))
  }

  @Test
  fun `authorize denies another user with 403`() {
    whenever(workbenchService.authorizeAccess(eq("mallory"), eq("wb-1.continuum.example.com"))).thenReturn(false)

    mockMvc.perform(
      get("/api/v1/workbench/authorize")
        .header("x-continuum-user-id", "mallory")
        .header("X-Forwarded-Host", "wb-1.continuum.example.com")
    ).andExpect(status().isForbidden)
  }

  @Test
  fun `authorize without X-Forwarded-Host is a 400 problem naming the header`() {
    mockMvc.perform(get("/api/v1/workbench/authorize").header("x-continuum-user-id", "alice"))
      .andExpect(status().isBadRequest)
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.error").value(containsString("X-Forwarded-Host")))
  }

  @Test
  fun `the root redirect keeps the external prefix`() {
    mockMvc.perform(get("/").header("X-Forwarded-Prefix", "/cluster-manager"))
      .andExpect(status().is3xxRedirection)
      .andExpect(header().string("Location", containsString("/cluster-manager/ui/")))
  }
}
