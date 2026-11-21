package org.projectcontinuum.core.credentials.openapi

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.projectcontinuum.core.credentials.config.H2JdbcConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * `CONTINUUM_API_DOCS_ENABLED=false` (the Helm chart's production setting) removes the spec and Swagger UI, and the
 * `/` redirect keeps the proxy's `X-Forwarded-Prefix` (`/credentials-manager`).
 */
@SpringBootTest(
  properties = [
    OpenApiSpecTest.MAIN_APPLICATION_YML,
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.sql.init.schema-locations=classpath:schema-h2.sql",
    "continuum.core.credentials.encryption.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "CONTINUUM_API_DOCS_ENABLED=false"
  ]
)
@Import(H2JdbcConfig::class)
@AutoConfigureMockMvc
class OpenApiDisabledTest {

  @Autowired
  private lateinit var mockMvc: MockMvc

  @Test
  fun `api docs and swagger ui are not served`() {
    for (path in listOf("/v3/api-docs", "/v3/api-docs/public", "/swagger-ui/index.html", "/swagger-ui.html")) {
      mockMvc.perform(get(path)).andExpect(status().isNotFound)
    }
  }

  @Test
  fun `the root redirect keeps the external prefix`() {
    mockMvc.perform(get("/").header("X-Forwarded-Prefix", "/credentials-manager"))
      .andExpect(status().is3xxRedirection)
      .andExpect(header().string("Location", containsString("/credentials-manager/ui/")))
  }
}
