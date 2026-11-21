package org.projectcontinuum.core.api.server.openapi

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** `CONTINUUM_API_DOCS_ENABLED=false` (the Helm chart's production setting) removes the spec and Swagger UI. */
@SpringBootTest(
  properties = [
    FullContextWithoutExternalSystems.MAIN_APPLICATION_YML,
    FullContextWithoutExternalSystems.H2_DATASOURCE,
    FullContextWithoutExternalSystems.H2_DRIVER,
    FullContextWithoutExternalSystems.H2_USER,
    FullContextWithoutExternalSystems.H2_PASSWORD,
    FullContextWithoutExternalSystems.H2_DIALECT,
    FullContextWithoutExternalSystems.NO_SCHEMA_INIT,
    FullContextWithoutExternalSystems.NO_MINIO_BUCKET_INIT,
    FullContextWithoutExternalSystems.TEMPORAL_TARGET,
    "CONTINUUM_API_DOCS_ENABLED=false",
    "continuum.core.api-docs.internal-group-enabled=true"
  ]
)
@AutoConfigureMockMvc
class OpenApiDisabledTest : FullContextWithoutExternalSystems() {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Test
  fun `api docs and swagger ui are not served`() {
    for (path in listOf("/v3/api-docs", "/v3/api-docs/public", "/v3/api-docs/internal", "/swagger-ui/index.html", "/swagger-ui.html")) {
      mockMvc.perform(get(path)).andExpect(status().isNotFound)
    }
  }

  @Test
  fun `the root does not redirect to the disabled swagger ui`() {
    mockMvc.perform(get("/")).andExpect(status().isNotFound)
  }
}
