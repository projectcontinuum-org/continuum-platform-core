package org.projectcontinuum.core.credentials.config

import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
  @Bean
  fun relativeServerCustomizer() = OpenApiCustomizer { openApi ->
    openApi.servers = listOf(Server().apply { url = "../" })
  }
}
