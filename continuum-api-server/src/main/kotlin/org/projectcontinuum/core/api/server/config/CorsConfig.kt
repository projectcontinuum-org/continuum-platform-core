package org.projectcontinuum.core.api.server.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
  /** `/` opens Swagger UI only while it is served; with the docs disabled (production) `/` is a plain 404. */
  @param:Value("\${springdoc.swagger-ui.enabled:true}")
  private val swaggerUiEnabled: Boolean
) {
  @Bean
  fun corsConfigurer(): WebMvcConfigurer {
    return object : WebMvcConfigurer {
      override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
          .allowedOrigins("*")
          .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
          .allowedHeaders("*")
      }

      override fun addViewControllers(registry: ViewControllerRegistry) {
        if (swaggerUiEnabled) {
          registry.addRedirectViewController("/", "/swagger-ui/index.html")
        }
      }
    }
  }
}
