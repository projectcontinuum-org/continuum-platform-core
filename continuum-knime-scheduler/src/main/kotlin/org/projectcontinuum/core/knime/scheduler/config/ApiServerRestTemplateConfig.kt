package org.projectcontinuum.core.knime.scheduler.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class ApiServerRestTemplateConfig {

  @Bean
  fun apiServerRestTemplate(): RestTemplate {
    val requestFactory = SimpleClientHttpRequestFactory().apply {
      setConnectTimeout(30000)
      setReadTimeout(30000)
    }
    return RestTemplate(requestFactory)
  }
}
