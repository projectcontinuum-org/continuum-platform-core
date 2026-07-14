package org.projectcontinuum.core.cluster.manager.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientConfig {

  @Bean
  fun restClient(): RestClient {
    return RestClient.builder()
      .requestFactory(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(Duration.ofSeconds(5))
        setReadTimeout(Duration.ofSeconds(10))
      })
      .build()
  }
}
