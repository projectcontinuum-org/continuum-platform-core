package com.continuum.base.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

/**
 * Configuration for RestTemplate bean used by REST Node.
 *
 * Provides a configured RestTemplate with sensible defaults for HTTP operations:
 * - 30 second connection timeout
 * - 30 second read timeout
 *
 * This bean can be easily mocked or replaced in tests for better testability.
 */
@Configuration
class RestTemplateConfig {

  /**
   * Creates a RestTemplate bean with configured timeouts.
   *
   * @return Configured RestTemplate instance
   */
  @Bean
  fun restTemplate(): RestTemplate {
    val requestFactory = SimpleClientHttpRequestFactory().apply {
      setConnectTimeout(30000) // 30 seconds
      setReadTimeout(30000)    // 30 seconds
    }

    return RestTemplate(requestFactory)
  }
}
