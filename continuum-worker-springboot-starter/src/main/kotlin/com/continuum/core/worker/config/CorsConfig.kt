package com.continuum.core.worker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Configuration class for CORS (Cross-Origin Resource Sharing) settings.
 *
 * This class provides CORS configuration to allow cross-origin requests
 * from any origin for the application's REST endpoints.
 *
 * @author Continuum Team
 * @since 1.0.0
 */
@Configuration
class CorsConfig {

  /**
   * Configures CORS mappings for all endpoints.
   *
   * Allows requests from any origin with GET, POST, PUT, DELETE, and OPTIONS methods.
   *
   * @return A [WebMvcConfigurer] with CORS mappings configured
   */
  @Bean
  fun corsConfigurer(): WebMvcConfigurer {
    return object : WebMvcConfigurer {
      override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
          .allowedOrigins("*")
          .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
          .allowedHeaders("*")
      }
    }
  }
}