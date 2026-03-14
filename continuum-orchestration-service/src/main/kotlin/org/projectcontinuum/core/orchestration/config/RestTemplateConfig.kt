package org.projectcontinuum.core.orchestration.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@Configuration
class RestTemplateConfig {

    @Bean
    fun orchestrationRestTemplate(): RestTemplate {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(30000) // 30 seconds
            setReadTimeout(30000)    // 30 seconds
        }

        return RestTemplate(requestFactory)
    }
}
