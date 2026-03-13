package org.projectcontinuum.core.worker.starter.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

/**
 * Default configuration for RestTemplate bean.
 *
 * Provides a configured RestTemplate with sensible defaults for HTTP operations:
 * - 30 second connection timeout
 * - 30 second read timeout
 *
 * This configuration is only activated when no RestTemplate bean is already defined
 * by the application, allowing feature modules to provide their own RestTemplate.
 */
@Configuration
class ContinuumWorkerSpringbootStarterRestTemplateConfig {

  /**
   * Creates a RestTemplate bean with configured timeouts.
   *
   * @return Configured RestTemplate instance
   */
  @Bean
  fun continuumWorkerSpringbootStarterRestTemplate(): RestTemplate {
    val requestFactory = SimpleClientHttpRequestFactory().apply {
      setConnectTimeout(30000) // 30 seconds
      setReadTimeout(30000)    // 30 seconds
    }

    return RestTemplate(requestFactory)
  }
}
