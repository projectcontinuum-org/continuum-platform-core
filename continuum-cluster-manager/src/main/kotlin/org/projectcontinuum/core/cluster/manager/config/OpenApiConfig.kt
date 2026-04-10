package org.projectcontinuum.core.cluster.manager.config

import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
  @Bean
  fun relativeServerCustomizer() = OpenApiCustomizer { openApi ->
    // Use a relative server URL so Swagger UI resolves API calls correctly
    // whether accessed directly or through the cloud-gateway reverse proxy.
    openApi.servers = listOf(Server().apply { url = "../" })
  }
}
